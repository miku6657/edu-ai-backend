package com.edu.ai.task.service;

import com.edu.ai.common.BusinessException;

import com.edu.ai.task.ai.DdlTaskExtractGateway;

import com.edu.ai.task.dto.*;

import com.edu.ai.task.entity.DdlTask;
import com.edu.ai.task.mapper.DdlTaskMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class DdlTaskExtractService {

    private final DdlTaskExtractGateway extractGateway;

    private final DdlTaskDuplicateService duplicateService;

    private final DdlTaskMapper taskMapper;

    private final TaskChangeDraftService draftService;

    public DdlTaskExtractService(
            DdlTaskExtractGateway extractGateway,
            DdlTaskDuplicateService duplicateService,
            DdlTaskMapper taskMapper,
            TaskChangeDraftService draftService) {

        this.extractGateway = extractGateway;
        this.duplicateService = duplicateService;
        this.taskMapper = taskMapper;
        this.draftService = draftService;
    }

    /**
     * AI 任务提取主流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public DdlExtractResult extract(
            Long userId,
            DdlExtractRequest request) {

        validateRequest(request);

        String timezone = request.getUserTimezone();

        if (timezone == null || timezone.isBlank()) {
            timezone = "Asia/Shanghai";
        }

        ZoneId zoneId;

        try {
            zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            throw new BusinessException(
                    400,
                    "不支持的时区"
            );
        }

        LocalDateTime now = LocalDateTime.now(zoneId);

        // 第一步：调用 AI 提取
        DdlTaskExtractedData data =
                extractGateway.extract(
                        request.getSourceText(),
                        request.getCourseName(),
                        timezone,
                        now
                );

        // 第二步：校验 AI 返回结果
        validateExtractedData(data);

        // 第三步：处理时间
        normalizeTime(data, now);

        // 第四步：查询重复任务
        List<DdlDuplicateCandidateVO> candidates =
                duplicateService.findCandidates(
                        userId,
                        request.getCourseId(),
                        data
                );

        // 第五步：处理重复情况
        if (!candidates.isEmpty()) {

            DdlDuplicateCandidateVO candidate =
                    candidates.get(0);

            DdlTask existing = taskMapper.selectById(
                    candidate.getTaskId()
            );

            if (existing == null) {
                throw new BusinessException(
                        409,
                        "重复任务数据已发生变化，请重新提取"
                );
            }

            // 标题完全一致且时间一致
            if (candidate.getSimilarity() >= 0.95
                    && duplicateService.isSameDeadline(
                            existing,
                            data
                    )) {

                DdlExtractResult result =
                        DdlExtractResult.of(
                                "IDEMPOTENT_HIT",
                                "该任务已经存在，无需重复创建"
                        );

                result.setTaskId(existing.getId());
                result.setExtractedData(data);

                return result;
            }

            // 相似任务但时间发生变化
            if (duplicateService.needsChangeConfirmation(
                    existing,
                    data)) {

                TaskChangeDraftCreateRequest draftRequest =
                        buildDraftRequest(data, request, timezone);

                TaskChangeDraftVO draft =
                        draftService.createDraft(
                                userId,
                                existing.getId(),
                                draftRequest
                        );

                DdlExtractResult result =
                        DdlExtractResult.of(
                                "CHANGE_DRAFT_CONFIRM_REQUIRED",
                                "发现已有相似任务，请确认是否更新"
                        );

                result.setMatchedTaskId(
                        existing.getId()
                );

                result.setChangeDraft(draft);
                result.setExtractedData(data);

                return result;
            }

            // 其他相似情况只提示，不自动覆盖
            DdlExtractResult result =
                    DdlExtractResult.of(
                            "SEMANTIC_DUPLICATE_HINT",
                            "发现可能重复的任务，请检查"
                    );

            result.setDuplicateCandidates(candidates);
            result.setExtractedData(data);

            return result;
        }

        // 第六步：没有重复任务，创建任务
        DdlTask task = buildTask(
                userId,
                request,
                data,
                timezone,
                now
        );

        taskMapper.insert(task);

        DdlExtractResult result =
                DdlExtractResult.of(
                        "CREATED",
                        "成功提取并创建任务"
                );

        result.setTaskId(task.getId());
        result.setExtractedData(data);

        return result;
    }

    /**
     * 构造 DDL 任务。
     */
    private DdlTask buildTask(
            Long userId,
            DdlExtractRequest request,
            DdlTaskExtractedData data,
            String timezone,
            LocalDateTime now) {

        DdlTask task = new DdlTask();

        task.setUserId(userId);

        task.setCourseId(request.getCourseId());
        task.setCourseName(
                data.getCourseName() != null
                        ? data.getCourseName()
                        : request.getCourseName()
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

        task.setTimeAmbiguityReason(
                data.getTimeAmbiguityReason()
        );

        task.setDeliverableFormat(
                data.getDeliverableFormat()
        );

        task.setSubmissionChannel(
                data.getSubmissionChannel()
        );

        task.setRawSourceText(request.getSourceText());
        task.setRawTimeText(data.getRawTimeText());

        task.setUserTimezone(timezone);

        task.setConfidence(
                data.getConfidence() == null
                        ? null
                        : BigDecimal.valueOf(data.getConfidence())
        );

        task.setStatus(
                data.getDueAt() == null
                        ? "PENDING_TIME"
                        : data.getDueAt().isBefore(now)
                        ? "OVERDUE"
                        : "ACTIVE"
        );

        task.setRelativeTime(
                Boolean.TRUE.equals(data.getTimeInferred())
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
     * 构造任务变更草案。
     */
    private TaskChangeDraftCreateRequest buildDraftRequest(
            DdlTaskExtractedData data,
            DdlExtractRequest request,
            String timezone) {

        TaskChangeDraftCreateRequest draft =
                new TaskChangeDraftCreateRequest();

        draft.setNewTaskTitle(data.getTaskTitle());
        draft.setNewTimePrecision(data.getTimePrecision());

        draft.setNewDueDate(data.getDueDate());
        draft.setNewDueTime(data.getDueTime());
        draft.setNewDueAt(data.getDueAt());

        draft.setNewTimeInferred(data.getTimeInferred());

        draft.setNewTimeAmbiguityReason(
                data.getTimeAmbiguityReason()
        );

        draft.setNewDeliverableFormat(
                data.getDeliverableFormat()
        );

        draft.setNewSubmissionChannel(
                data.getSubmissionChannel()
        );

        draft.setRawSourceText(request.getSourceText());
        draft.setRawTimeText(data.getRawTimeText());
        draft.setUserTimezone(timezone);

        return draft;
    }

    /**
     * 时间标准化。
     */
    private void normalizeTime(
            DdlTaskExtractedData data,
            LocalDateTime now) {

        if (data.getDueAt() != null) {

            LocalDateTime dueAt = data.getDueAt();

            if (data.getDueDate() != null
                    && !data.getDueDate().equals(dueAt.toLocalDate())) {
                throw new BusinessException(422, "截止日期不一致");
            }

            if (data.getDueTime() != null
                    && !data.getDueTime().equals(dueAt.toLocalTime())) {
                throw new BusinessException(422, "截止时间不一致");
            }

            data.setDueDate(dueAt.toLocalDate());
            data.setDueTime(dueAt.toLocalTime());
            data.setTimePrecision("DATE_TIME");

            return;
        }

        if (data.getDueDate() != null
                && data.getDueTime() != null) {

            data.setDueAt(
                    LocalDateTime.of(
                            data.getDueDate(),
                            data.getDueTime()
                    )
            );

            data.setTimePrecision("DATE_TIME");

        } else if (data.getDueDate() != null) {

            data.setTimePrecision("DATE");

        } else if (data.getDueTime() != null) {

            data.setTimePrecision("TIME");

        } else {

            data.setTimePrecision("UNKNOWN");
        }
    }

    private void validateRequest(
            DdlExtractRequest request) {

        if (request == null
                || request.getSourceText() == null
                || request.getSourceText().isBlank()) {

            throw new BusinessException(
                    400,
                    "提取文本不能为空"
            );
        }

        if (request.getSourceText().length() > 10000) {
            throw new BusinessException(
                    400,
                    "提取文本不能超过10000个字符"
            );
        }
    }

    private void validateExtractedData(
            DdlTaskExtractedData data) {

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
