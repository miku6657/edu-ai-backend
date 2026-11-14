package com.edu.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.task.ai.DdlTaskExtractGateway;
import com.edu.ai.task.dto.*;
import com.edu.ai.task.entity.DdlTask;
import com.edu.ai.task.mapper.DdlTaskMapper;

import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class DdlTaskExtractService {

    private final DdlTaskExtractGateway extractGateway;
    private final DdlTaskDuplicateService duplicateService;
    private final DdlTaskMapper taskMapper;
    private final TaskChangeDraftService draftService;
    private final CourseMapper courseMapper;
    private final DdlExtractIdempotencyService idempotencyService;
    private final DdlRequestHashService hashService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final DdlDisplayCodeGenerator displayCodeGenerator;
    private final DdlTaskStatusResolver statusResolver;

    public DdlTaskExtractService(
            DdlTaskExtractGateway extractGateway,
            DdlTaskDuplicateService duplicateService,
            DdlTaskMapper taskMapper,
            TaskChangeDraftService draftService,
            CourseMapper courseMapper,
            DdlExtractIdempotencyService idempotencyService,
            DdlRequestHashService hashService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            DdlDisplayCodeGenerator displayCodeGenerator,
            DdlTaskStatusResolver statusResolver) {

        this.extractGateway = extractGateway;
        this.duplicateService = duplicateService;
        this.taskMapper = taskMapper;
        this.draftService = draftService;
        this.courseMapper = courseMapper;
        this.idempotencyService = idempotencyService;
        this.hashService = hashService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.displayCodeGenerator = displayCodeGenerator;
        this.statusResolver = statusResolver;
    }

    /**
     * AI 提取任务主流程（接入数据库幂等）
     */
    public DdlExtractResult extract(
            Long userId,
            DdlExtractRequest request) {

        validateRequest(request);

        String timezone = "Asia/Shanghai";
        ZoneId zoneId = ZoneId.of(timezone);

        LocalDateTime now;

        if (request.getReferenceTime() == null
                || request.getReferenceTime().isBlank()) {

            now = LocalDateTime.now(zoneId);

        } else {

            try {
                now = OffsetDateTime.parse(
                        request.getReferenceTime()
                ).atZoneSameInstant(zoneId).toLocalDateTime();

            } catch (Exception e) {
                throw new BusinessException(
                        400,
                        "reference_time 格式不正确"
                );
            }
        }

        // 1. 校验课程归属
        String courseName = resolveCourseName(
                userId,
                request.getCourseId()
        );

        // 2. 计算完整请求摘要
        String requestHash = hashService.hashRequest(request);

        // 3. 登记请求，独立提交 PROCESSING
        var idempotencyRecord = idempotencyService.acquire(
                userId,
                request.getClientRequestId(),
                requestHash
        );

        // 4. 如果之前已经成功，直接恢复原始响应
        if ("SUCCEEDED".equals(idempotencyRecord.getStatus())) {

            try {
                return objectMapper.readValue(
                        idempotencyRecord.getResponseJson(),
                        DdlExtractResult.class
                );

            } catch (Exception e) {
                throw new BusinessException(
                        500,
                        "历史提取结果无法恢复，请联系管理员"
                );
            }
        }

        try {

            // 5. 调用 AI
            DdlTaskExtractedData data =
                    extractGateway.extract(
                            request.getSourceText(),
                            courseName,
                            timezone,
                            now
                    );

            // 6. 校验 AI 结果
            validateExtractedData(data);

            // 7. 标准化时间
            normalizeTime(data, now);

            // 8. 一个事务内完成业务操作和幂等结果保存
            return transactionTemplate.execute(status -> {

                List<DdlDuplicateCandidateVO> candidates;

                if (Boolean.TRUE.equals(request.getForceCreate())) {
                    candidates = List.of();
                } else {
                    candidates = duplicateService.findCandidates(
                            userId,
                            request.getCourseId(),
                            data
                    );
                }

                // 9. 处理相似任务
                if (!candidates.isEmpty()) {

                    DdlDuplicateCandidateVO candidate =
                            candidates.get(0);

                    DdlTask existing = taskMapper.selectOne(
                            new LambdaQueryWrapper<DdlTask>()
                                    .eq(DdlTask::getId,
                                            candidate.getTaskId())
                                    .eq(DdlTask::getUserId, userId)
                                    .last("LIMIT 1")
                    );

                    if (existing == null) {
                        throw new BusinessException(
                                409,
                                "重复任务数据已发生变化，请重新提取"
                        );
                    }

                    // 完全相同的任务
                    if (candidate.getSimilarity() >= 0.95
                            && duplicateService.isSameDeadline(
                                    existing, data)) {

                        return saveIdempotentResult(
                                idempotencyRecord.getId(),
                                idempotencyRecord.getProcessingToken(),
                                "IDEMPOTENT_HIT",
                                existing,
                                null
                        );
                    }

                    // 相似任务但截止时间发生变化
                    if (duplicateService.needsChangeConfirmation(
                            existing, data)) {

                        TaskChangeDraftCreateRequest draftRequest =
                                buildDraftRequest(
                                        data,
                                        request,
                                        timezone
                                );

                        TaskChangeDraftVO draft =
                                draftService.createDraft(
                                        userId,
                                        existing.getId(),
                                        draftRequest
                                );

                        return saveIdempotentResult(
                                idempotencyRecord.getId(),
                                idempotencyRecord.getProcessingToken(),
                                "CHANGE_DRAFT_CONFIRM_REQUIRED",
                                existing,
                                draft
                        );
                    }

                    // 其他相似任务
                    return saveIdempotentResult(
                            idempotencyRecord.getId(),
                            idempotencyRecord.getProcessingToken(),
                            "SEMANTIC_DUPLICATE_HINT",
                            existing,
                            null
                    );
                }

                // 10. 创建新任务
                DdlTask task = buildTask(
                        userId,
                        request,
                        data,
                        timezone,
                        courseName,
                        now
                );

                taskMapper.insert(task);

                // 11. 保存完整响应和 SUCCEEDED 状态
                return saveIdempotentResult(
                        idempotencyRecord.getId(),
                        idempotencyRecord.getProcessingToken(),
                        "CREATED",
                        task,
                        null
                );
            });

        } catch (RuntimeException e) {

            // 12. 业务事务失败，独立记录失败状态
            try {
                idempotencyService.fail(
                        idempotencyRecord.getId(),
                        idempotencyRecord.getProcessingToken(),
                        e.getMessage()
                );
            } catch (RuntimeException recordException) {
                e.addSuppressed(recordException);
            }

            throw e;
        }
    }

    /**
     * 保存幂等结果。
     */
    private DdlExtractResult saveIdempotentResult(
            Long requestId,
            String token,
            String outcome,
            DdlTask task,
            TaskChangeDraftVO draft) {

        DdlExtractResult result = buildResult(
                outcome,
                task,
                draft
        );

        String responseJson;

        try {
            responseJson = objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new BusinessException(
                    500,
                    "提取结果序列化失败"
            );
        }

        idempotencyService.complete(
                requestId,
                token,
                task == null ? null : task.getId(),
                outcome,
                responseJson
        );

        return result;
    }

    /**
     * 构造新版统一返回对象
     */
    private DdlExtractResult buildResult(
            String outcome,
            DdlTask task,
            TaskChangeDraftVO draft) {

        DdlExtractResult result = new DdlExtractResult();

        result.setExtractOutcome(outcome);
        result.setTask(
                task == null ? null : DdlTaskVO.from(task)
        );
        result.setChangeDraft(draft);

        return result;
    }

    /**
     * 构造任务实体
     */
    private DdlTask buildTask(
            Long userId,
            DdlExtractRequest request,
            DdlTaskExtractedData data,
            String timezone,
            String courseName,
            LocalDateTime now) {

        DdlTask task = new DdlTask();

        task.setUserId(userId);

        task.setDisplayCode(
                displayCodeGenerator.generate()
        );

        task.setCourseId(request.getCourseId());

        task.setCourseName(
                data.getCourseName() != null
                        ? data.getCourseName()
                        : courseName
        );

        task.setTaskTitle(data.getTaskTitle());
        task.setDescription(data.getDescription());

        task.setTimePrecision(data.getTimePrecision());

        task.setDueDate(data.getDueDate());
        task.setDueTime(data.getDueTime());
        task.setDueAt(data.getDueAt());

        task.setTimeInferred(
                Boolean.TRUE.equals(data.getTimeInferred())
        );

        task.setRelativeTime(
                Boolean.TRUE.equals(data.getRelativeTime())
        );

        task.setTimeAmbiguityReason(
                data.getTimeAmbiguityReason()
        );

        task.setDeliverableFormat(data.getDeliverableFormat());
        task.setSubmissionChannel(data.getSubmissionChannel());

        task.setRawSourceText(request.getSourceText());
        task.setRawTimeText(data.getRawTimeText());

        task.setUserTimezone(timezone);

        task.setConfidence(
                data.getConfidence() == null
                        ? null
                        : BigDecimal.valueOf(data.getConfidence())
        );

        task.setStatus(
                statusResolver.resolve(
                        data.getTimePrecision(),
                        data.getDueAt(),
                        now
                )
        );

        task.setFollowUpCount(0);

        task.setRemindedT24h(false);
        task.setRemindedT6h(false);
        task.setRemindedOverdue(false);

        task.setSourcePlatform("AI");

        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        return task;
    }

    /**
     * 构造任务变更草案请求
     */
    private TaskChangeDraftCreateRequest buildDraftRequest(
            DdlTaskExtractedData data,
            DdlExtractRequest request,
            String timezone) {

        TaskChangeDraftCreateRequest draft =
                new TaskChangeDraftCreateRequest();

        draft.setNewTaskTitle(data.getTaskTitle());
        draft.setNewDescription(data.getDescription());
        draft.setNewTimePrecision(data.getTimePrecision());

        draft.setNewDueDate(data.getDueDate());
        draft.setNewDueTime(data.getDueTime());
        draft.setNewDueAt(data.getDueAt());

        draft.setNewTimeInferred(data.getTimeInferred());

        draft.setNewRelativeTime(
                data.getRelativeTime()
        );

        draft.setNewConfidence(
                data.getConfidence() == null
                        ? null
                        : BigDecimal.valueOf(data.getConfidence())
        );

        draft.setNewTimeAmbiguityReason(
                data.getTimeAmbiguityReason()
        );

        draft.setNewDeliverableFormat(data.getDeliverableFormat());
        draft.setNewSubmissionChannel(data.getSubmissionChannel());

        draft.setRawSourceText(request.getSourceText());
        draft.setRawTimeText(data.getRawTimeText());
        draft.setUserTimezone(timezone);

        return draft;
    }

    /**
     * 从课程表查询课程名称，并校验归属。
     */
    private String resolveCourseName(Long userId, Long courseId) {

        if (courseId == null) {
            return null;
        }

        Course course = courseMapper.selectOne(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getId, courseId)
                        .eq(Course::getUserId, userId)
                        .eq(Course::getStatus, 1)
                        .last("LIMIT 1")
        );

        if (course == null) {
            throw new BusinessException(
                    404,
                    "课程不存在或无权使用"
            );
        }

        return course.getName();
    }

    /**
     * 时间标准化
     */
    private void normalizeTime(
            DdlTaskExtractedData data,
            LocalDateTime now) {

        // AI 已经提供完整截止时间
        if (data.getDueAt() != null) {

            LocalDateTime dueAt = data.getDueAt();

            if (data.getDueDate() != null
                    && !data.getDueDate().equals(
                            dueAt.toLocalDate())) {
                throw new BusinessException(
                        422, "截止日期不一致"
                );
            }

            if (data.getDueTime() != null
                    && !data.getDueTime().equals(
                            dueAt.toLocalTime())) {
                throw new BusinessException(
                        422, "截止时间不一致"
                );
            }

            data.setDueDate(dueAt.toLocalDate());
            data.setDueTime(dueAt.toLocalTime());

            data.setTimePrecision(
                    !dueAt.isAfter(now)
                            ? "INVALID_OR_PAST"
                            : "EXACT_DATETIME"
            );

            data.setTimeInferred(false);
            return;
        }

        // 日期和时间都有
        if (data.getDueDate() != null
                && data.getDueTime() != null) {

            LocalDateTime dueAt = LocalDateTime.of(
                    data.getDueDate(),
                    data.getDueTime()
            );

            data.setDueAt(dueAt);

            data.setTimePrecision(
                    !dueAt.isAfter(now)
                            ? "INVALID_OR_PAST"
                            : "EXACT_DATETIME"
            );

            data.setTimeInferred(false);
            return;
        }

        // 只有日期
        if (data.getDueDate() != null) {

            data.setDueTime(null);

            data.setDueAt(
                    data.getDueDate().atTime(23, 59, 59)
            );

            data.setTimePrecision("DATE_ONLY");
            data.setTimeInferred(true);
            return;
        }

        // 没有可用截止日期
        data.setDueDate(null);
        data.setDueTime(null);
        data.setDueAt(null);

        data.setTimePrecision("VAGUE_OR_MISSING");
        data.setTimeInferred(false);
    }

    /**
     * 校验请求
     */
    private void validateRequest(DdlExtractRequest request) {

        if (request == null
                || request.getSourceText() == null
                || request.getSourceText().isBlank()) {

            throw new BusinessException(400, "提取文本不能为空");
        }

        if (request.getSourceText().length() > 10000) {
            throw new BusinessException(400, "提取文本不能超过10000个字符");
        }

        if (request.getClientRequestId() == null
                || request.getClientRequestId().isBlank()) {

            throw new BusinessException(400, "请求编号不能为空");
        }
    }

    /**
     * 校验 AI 返回结果
     */
    private void validateExtractedData(DdlTaskExtractedData data) {

        if (data == null
                || data.getTaskTitle() == null
                || data.getTaskTitle().isBlank()) {

            throw new BusinessException(
                    422,
                    "未能识别有效的任务名称"
            );
        }

        if (data.getTaskTitle().length() > 200) {
            throw new BusinessException(
                    422,
                    "识别出的任务名称过长"
            );
        }

        if (data.getConfidence() != null
                && (data.getConfidence() < 0
                || data.getConfidence() > 1)) {

            throw new BusinessException(
                    422,
                    "AI 置信度不合法"
            );
        }
    }
}
