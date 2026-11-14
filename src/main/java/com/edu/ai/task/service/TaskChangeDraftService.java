package com.edu.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.edu.ai.common.BusinessException;

import com.edu.ai.task.dto.DraftDecisionRequest;
import com.edu.ai.task.dto.TaskChangeDraftCreateRequest;
import com.edu.ai.task.dto.TaskChangeDraftVO;

import com.edu.ai.task.entity.DdlTask;
import com.edu.ai.task.entity.TaskChangeDraft;

import com.edu.ai.task.mapper.DdlTaskMapper;
import com.edu.ai.task.mapper.TaskChangeDraftMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskChangeDraftService {

    private static final int EXPIRE_HOURS = 24;

    private final DdlTaskMapper taskMapper;
    private final TaskChangeDraftMapper draftMapper;
    private final DdlDisplayCodeGenerator displayCodeGenerator;
    private final DdlTaskStatusResolver statusResolver;

    public TaskChangeDraftService(
            DdlTaskMapper taskMapper,
            TaskChangeDraftMapper draftMapper,
             DdlDisplayCodeGenerator displayCodeGenerator,
            DdlTaskStatusResolver statusResolver) {

        this.taskMapper = taskMapper;
        this.draftMapper = draftMapper;
        this.displayCodeGenerator = displayCodeGenerator;
        this.statusResolver = statusResolver;
    }

    /**
     * 创建任务变更草案。
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskChangeDraftVO createDraft(
            Long userId,
            Long targetTaskId,
            TaskChangeDraftCreateRequest request) {

        if (request == null
                || request.getNewTaskTitle() == null
                || request.getNewTaskTitle().isBlank()
                || request.getNewTaskTitle().length() > 200
                || request.getNewTimePrecision() == null
                || request.getNewTimePrecision().isBlank()) {

            throw new BusinessException(
                    400,
                    "草案参数不完整或不合法"
            );
        }

        if (!List.of(
                "EXACT_DATETIME",
                "DATE_ONLY",
                "VAGUE_OR_MISSING"
        ).contains(request.getNewTimePrecision())) {

            throw new BusinessException(
                    400,
                    "不支持的时间精度"
            );
        }

        DdlTask original = taskMapper.selectOne(
                new LambdaQueryWrapper<DdlTask>()
                        .eq(DdlTask::getId, targetTaskId)
                        .eq(DdlTask::getUserId, userId)
                        .last("LIMIT 1 FOR UPDATE")
        );

        if (original == null) {
            throw new BusinessException(
                    404,
                    "任务不存在或无权访问"
            );
        }

        if ("COMPLETED".equals(original.getStatus())
                || "CANCELED".equals(original.getStatus())) {

            throw new BusinessException(
                    409,
                    "已完成或已取消的任务不能创建变更草案"
            );
        }

        // 先把已过期的草案标记为 EXPIRED（仅当前任务）
        draftMapper.update(
                null,
                new LambdaUpdateWrapper<TaskChangeDraft>()
                        .eq(TaskChangeDraft::getUserId, userId)
                        .eq(TaskChangeDraft::getTargetTaskId,
                                targetTaskId)
                        .eq(TaskChangeDraft::getStatus, "PENDING")
                        .le(TaskChangeDraft::getExpiresAt,
                                LocalDateTime.now())
                        .set(TaskChangeDraft::getStatus, "EXPIRED")
        );

        // 检查是否已经有待确认的草案
        TaskChangeDraft existing = draftMapper.selectOne(
                new LambdaQueryWrapper<TaskChangeDraft>()
                        .eq(TaskChangeDraft::getTargetTaskId,
                                targetTaskId)
                        .eq(TaskChangeDraft::getUserId, userId)
                        .eq(TaskChangeDraft::getStatus, "PENDING")
                        .last("LIMIT 1")
        );

        if (existing != null) {
            throw new BusinessException(
                    409,
                    "该任务已有待确认的变更草案"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        TaskChangeDraft draft = new TaskChangeDraft();

        draft.setTargetTaskId(targetTaskId);
        draft.setUserId(userId);

        draft.setNewTaskTitle(
                request.getNewTaskTitle().trim()
        );

        draft.setNewDescription(
                request.getNewDescription()
        );

        draft.setNewRelativeTime(
                Boolean.TRUE.equals(
                        request.getNewRelativeTime()
                )
        );

        draft.setNewConfidence(
                request.getNewConfidence()
        );

        draft.setNewTimePrecision(
                request.getNewTimePrecision()
        );

        draft.setNewDueDate(request.getNewDueDate());
        draft.setNewDueTime(request.getNewDueTime());
        draft.setNewDueAt(request.getNewDueAt());

        draft.setNewTimeInferred(
                Boolean.TRUE.equals(request.getNewTimeInferred())
        );

        draft.setNewTimeAmbiguityReason(
                request.getNewTimeAmbiguityReason()
        );

        draft.setNewDeliverableFormat(
                request.getNewDeliverableFormat()
        );

        draft.setNewSubmissionChannel(
                request.getNewSubmissionChannel()
        );

        draft.setRawSourceText(request.getRawSourceText());
        draft.setRawTimeText(request.getRawTimeText());
        draft.setUserTimezone(request.getUserTimezone());

        draft.setExpiresAt(
                now.plusHours(EXPIRE_HOURS)
        );

        draft.setStatus("PENDING");
        draft.setCreateTime(now);
        draft.setUpdateTime(now);

        draftMapper.insert(draft);

        return TaskChangeDraftVO.from(draft);
    }

    /**
     * 查询当前用户所有待确认的变更草案。
     */
    public List<TaskChangeDraftVO> listPending(Long userId) {
        return listPending(userId, null);
    }

    /**
     * 根据用户和课程查询待确认的变更草案。
     *
     * courseId 为 null 时查询用户全部课程。
     */
    public List<TaskChangeDraftVO> listPending(
            Long userId,
            Long courseId) {

        LocalDateTime now = LocalDateTime.now();

        // 先把过期草案更新为 EXPIRED
        draftMapper.update(
                null,
                new LambdaUpdateWrapper<TaskChangeDraft>()
                        .eq(TaskChangeDraft::getUserId, userId)
                        .eq(TaskChangeDraft::getStatus, "PENDING")
                        .le(TaskChangeDraft::getExpiresAt, now)
                        .set(TaskChangeDraft::getStatus, "EXPIRED")
                        .set(TaskChangeDraft::getUpdateTime, now)
        );

        LambdaQueryWrapper<TaskChangeDraft> wrapper =
                new LambdaQueryWrapper<TaskChangeDraft>()
                        .eq(TaskChangeDraft::getUserId, userId)
                        .eq(TaskChangeDraft::getStatus, "PENDING")
                        .gt(TaskChangeDraft::getExpiresAt, now)
                        .orderByAsc(TaskChangeDraft::getExpiresAt);

        List<TaskChangeDraft> drafts =
                draftMapper.selectList(wrapper);

        if (courseId == null || drafts.isEmpty()) {
            return drafts.stream()
                    .map(TaskChangeDraftVO::from)
                    .toList();
        }

        // 查询当前用户在指定课程下的任务 ID
        List<Long> taskIds = taskMapper.selectList(
                new LambdaQueryWrapper<DdlTask>()
                        .select(DdlTask::getId)
                        .eq(DdlTask::getUserId, userId)
                        .eq(DdlTask::getCourseId, courseId)
        ).stream()
                .map(DdlTask::getId)
                .toList();

        if (taskIds.isEmpty()) {
            return List.of();
        }

        return drafts.stream()
                .filter(draft ->
                        taskIds.contains(draft.getTargetTaskId()))
                .map(TaskChangeDraftVO::from)
                .toList();
    }

    /**
     * 确认变更草案。
     *
     * CONFIRM_UPDATE：更新原任务
     * CREATE_NEW：保留原任务，创建新任务
     * DISCARD：忽略草案
     */
    @Transactional(rollbackFor = Exception.class)
    public DdlTask decide(
            Long targetTaskId,
            Long userId,
            DraftDecisionRequest request) {

        if (request == null
                || request.getDecision() == null
                || request.getClientRequestId() == null
                || request.getClientRequestId().isBlank()) {

            throw new BusinessException(
                    400,
                    "草案决策参数不完整"
            );
        }

        String decision = request.getDecision().name();

        // 幂等：同一请求重复提交时，直接返回之前的处理结果
        TaskChangeDraft previous = draftMapper.selectOne(
                new LambdaQueryWrapper<TaskChangeDraft>()
                        .eq(TaskChangeDraft::getUserId, userId)
                        .eq(
                                TaskChangeDraft::getDecisionRequestId,
                                request.getClientRequestId()
                        )
                        .ne(TaskChangeDraft::getStatus, "PENDING")
                        .last("LIMIT 1")
        );

        if (previous != null) {

            if (!targetTaskId.equals(previous.getTargetTaskId())
                    || !decision.equals(previous.getDecision())) {

                throw new BusinessException(
                        409,
                        "请求编号已被其他操作使用"
                );
            }

            return taskMapper.selectById(
                    previous.getResultTaskId()
            );
        }

        DdlTask original = taskMapper.selectOne(
                new LambdaQueryWrapper<DdlTask>()
                        .eq(DdlTask::getId, targetTaskId)
                        .eq(DdlTask::getUserId, userId)
                        .last("LIMIT 1 FOR UPDATE")
        );

        if (original == null) {
            throw new BusinessException(
                    404,
                    "任务不存在或无权访问"
            );
        }

        TaskChangeDraft draft = draftMapper.selectOne(
                new LambdaQueryWrapper<TaskChangeDraft>()
                        .eq(TaskChangeDraft::getTargetTaskId,
                                targetTaskId)
                        .eq(TaskChangeDraft::getUserId, userId)
                        .eq(TaskChangeDraft::getStatus, "PENDING")
                        .last("LIMIT 1")
        );

        if (draft == null) {
            throw new BusinessException(
                    404,
                    "待确认的任务变更草案不存在"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        if (!draft.getExpiresAt().isAfter(now)) {
            throw new BusinessException(
                    409,
                    "变更草案已过期，请重新处理"
            );
        }

        // 原子抢占草案，防止并发重复处理
        int claimed = draftMapper.update(
                null,
                new LambdaUpdateWrapper<TaskChangeDraft>()
                        .eq(
                                TaskChangeDraft::getId,
                                draft.getId()
                        )
                        .eq(
                                TaskChangeDraft::getStatus,
                                "PENDING"
                        )
                        .gt(
                                TaskChangeDraft::getExpiresAt,
                                now
                        )
                        .set(
                                TaskChangeDraft::getStatus,
                                "PROCESSING"
                        )
                        .set(
                                TaskChangeDraft::getUpdateTime,
                                now
                        )
        );

        if (claimed != 1) {

            // 再次检查是否是同一个请求已经处理成功
            TaskChangeDraft previousResult =
                    draftMapper.selectOne(
                            new LambdaQueryWrapper<TaskChangeDraft>()
                                    .eq(
                                            TaskChangeDraft::getUserId,
                                            userId
                                    )
                                    .eq(
                                            TaskChangeDraft::getDecisionRequestId,
                                            request.getClientRequestId()
                                    )
                                    .last("LIMIT 1")
                    );

            if (previousResult != null) {

                if (!targetTaskId.equals(
                            previousResult.getTargetTaskId())
                        || !decision.equals(
                            previousResult.getDecision())) {

                    throw new BusinessException(
                            409,
                            "请求编号已被其他操作使用"
                    );
                }

                return taskMapper.selectById(
                        previousResult.getResultTaskId()
                );
            }

            throw new BusinessException(
                    409,
                    "草案已经被处理，请刷新后重试"
            );
        }

        DdlTask result;

        switch (decision) {

            case "CONFIRM_UPDATE" -> {

                applyDraft(original, draft, now);

                taskMapper.updateById(original);

                draft.setStatus("CONFIRMED");

                result = original;
            }

            case "CREATE_NEW" -> {

                DdlTask newTask = new DdlTask();

                newTask.setDisplayCode(
                        displayCodeGenerator.generate()
                );

                copyDraftToNewTask(
                        newTask,
                        original,
                        draft,
                        userId,
                        now
                );

                taskMapper.insert(newTask);

                draft.setStatus("CREATED_NEW");

                result = newTask;
            }

            case "DISCARD" -> {

                draft.setStatus("DISCARDED");

                result = original;
            }

            default -> throw new BusinessException(
                    400,
                    "不支持的草案决策类型"
            );
        }

        draft.setResultTaskId(result.getId());
        draft.setDecision(decision);
        draft.setDecisionRequestId(
                request.getClientRequestId()
        );
        draft.setDecidedAt(now);
        draft.setUpdateTime(now);

        draftMapper.updateById(draft);

        return result;
    }

    /**
     * 将草案中的信息更新到原任务。
     */
    private void applyDraft(
            DdlTask task,
            TaskChangeDraft draft,
            LocalDateTime now) {

        boolean deadlineChanged =
                !java.util.Objects.equals(
                        task.getDueDate(),
                        draft.getNewDueDate()
                )
                || !java.util.Objects.equals(
                        task.getDueTime(),
                        draft.getNewDueTime()
                )
                || !java.util.Objects.equals(
                        task.getDueAt(),
                        draft.getNewDueAt()
                )
                || !java.util.Objects.equals(
                        task.getTimePrecision(),
                        draft.getNewTimePrecision()
                );

        task.setTaskTitle(draft.getNewTaskTitle());
        task.setDescription(draft.getNewDescription());

        task.setTimePrecision(
                draft.getNewTimePrecision()
        );

        task.setDueDate(draft.getNewDueDate());
        task.setDueTime(draft.getNewDueTime());
        task.setDueAt(draft.getNewDueAt());

        task.setTimeInferred(
                Boolean.TRUE.equals(draft.getNewTimeInferred())
        );

        task.setRelativeTime(
                Boolean.TRUE.equals(draft.getNewRelativeTime())
        );

        task.setConfidence(draft.getNewConfidence());

        task.setTimeAmbiguityReason(
                draft.getNewTimeAmbiguityReason()
        );

        task.setDeliverableFormat(
                draft.getNewDeliverableFormat()
        );

        task.setSubmissionChannel(
                draft.getNewSubmissionChannel()
        );

        task.setRawSourceText(draft.getRawSourceText());
        task.setRawTimeText(draft.getRawTimeText());

        if (draft.getUserTimezone() != null
                && !draft.getUserTimezone().isBlank()) {

            task.setUserTimezone(draft.getUserTimezone());
        }

        task.setStatus(
                statusResolver.resolve(
                        draft.getNewTimePrecision(),
                        draft.getNewDueAt(),
                        now
                )
        );

        // 截止时间改变后，旧提醒状态不再有效
        if (deadlineChanged) {

            task.setFollowUpAt(null);
            task.setFollowUpCount(0);

            task.setRemindedT24h(false);
            task.setRemindedT6h(false);
            task.setRemindedOverdue(false);
        }

        task.setUpdatedAt(now);
    }

    /**
     * 从变更草案创建一条新任务。
     */
    private void copyDraftToNewTask(
            DdlTask task,
            DdlTask original,
            TaskChangeDraft draft,
            Long userId,
            LocalDateTime now) {

        task.setUserId(userId);

        task.setCourseId(original.getCourseId());
        task.setCourseName(original.getCourseName());

        task.setTaskTitle(draft.getNewTaskTitle());
        task.setDescription(draft.getNewDescription());

        task.setTimePrecision(
                draft.getNewTimePrecision()
        );

        task.setDueDate(draft.getNewDueDate());
        task.setDueTime(draft.getNewDueTime());
        task.setDueAt(draft.getNewDueAt());

        task.setTimeInferred(
                Boolean.TRUE.equals(draft.getNewTimeInferred())
        );

        task.setRelativeTime(
                Boolean.TRUE.equals(draft.getNewRelativeTime())
        );

        task.setTimeAmbiguityReason(
                draft.getNewTimeAmbiguityReason()
        );

        task.setDeliverableFormat(
                draft.getNewDeliverableFormat()
        );

        task.setSubmissionChannel(
                draft.getNewSubmissionChannel()
        );

        task.setRawSourceText(draft.getRawSourceText());
        task.setRawTimeText(draft.getRawTimeText());

        task.setUserTimezone(
                draft.getUserTimezone() != null
                        ? draft.getUserTimezone()
                        : original.getUserTimezone()
        );

        task.setConfidence(draft.getNewConfidence());

        task.setStatus(
                statusResolver.resolve(
                        draft.getNewTimePrecision(),
                        draft.getNewDueAt(),
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
    }

    /**
     * 校验任务归属。
     */
    private DdlTask requireOwnedTask(
            Long taskId,
            Long userId) {

        if (taskId == null || taskId <= 0) {
            throw new BusinessException(
                    400,
                    "任务ID不合法"
            );
        }

        DdlTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<DdlTask>()
                        .eq(DdlTask::getId, taskId)
                        .eq(DdlTask::getUserId, userId)
                        .last("LIMIT 1")
        );

        if (task == null) {
            throw new BusinessException(
                    404,
                    "任务不存在或无权访问"
            );
        }

        return task;
    }

}
