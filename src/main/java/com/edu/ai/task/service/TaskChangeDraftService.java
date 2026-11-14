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

    public TaskChangeDraftService(
            DdlTaskMapper taskMapper,
            TaskChangeDraftMapper draftMapper) {

        this.taskMapper = taskMapper;
        this.draftMapper = draftMapper;
    }

    /**
     * 创建任务变更草案。
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskChangeDraftVO createDraft(
            Long userId,
            Long targetTaskId,
            TaskChangeDraftCreateRequest request) {

        DdlTask original = requireOwnedTask(
                targetTaskId,
                userId
        );

        if ("COMPLETED".equals(original.getStatus())
                || "CANCELED".equals(original.getStatus())) {

            throw new BusinessException(
                    409,
                    "已完成或已取消的任务不能创建变更草案"
            );
        }

        // 先把已过期的草案标记为 EXPIRED
        draftMapper.update(
                null,
                new LambdaUpdateWrapper<TaskChangeDraft>()
                        .eq(TaskChangeDraft::getUserId, userId)
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

        return toVO(draft);
    }

    /**
     * 查询当前用户所有待确认的变更草案。
     */
    public List<TaskChangeDraftVO> listPending(Long userId) {

        List<TaskChangeDraft> drafts = draftMapper.selectList(
                new LambdaQueryWrapper<TaskChangeDraft>()
                        .eq(TaskChangeDraft::getUserId, userId)
                        .eq(TaskChangeDraft::getStatus, "PENDING")
                        .orderByAsc(TaskChangeDraft::getExpiresAt)
        );

        LocalDateTime now = LocalDateTime.now();

        return drafts.stream()
                .filter(draft ->
                        draft.getExpiresAt().isAfter(now))
                .map(this::toVO)
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

        DdlTask original = requireOwnedTask(
                targetTaskId,
                userId
        );

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
                        .eq(TaskChangeDraft::getId, draft.getId())
                        .eq(TaskChangeDraft::getStatus, "PENDING")
                        .set(TaskChangeDraft::getStatus, "PROCESSING")
        );

        if (claimed != 1) {
            throw new BusinessException(
                    409,
                    "草案已经被处理，请刷新后重试"
            );
        }

        DdlTask result;

        switch (decision) {

            case "CONFIRM_UPDATE" -> {

                applyDraft(original, draft);

                original.setUpdatedAt(now);

                taskMapper.updateById(original);

                draft.setStatus("CONFIRMED");

                result = original;
            }

            case "CREATE_NEW" -> {

                DdlTask newTask = new DdlTask();

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

        draft.setResultTaskId(result.getTaskId());
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
            TaskChangeDraft draft) {

        task.setTaskTitle(draft.getNewTaskTitle());

        task.setTimePrecision(
                draft.getNewTimePrecision()
        );

        task.setDueDate(draft.getNewDueDate());
        task.setDueTime(draft.getNewDueTime());
        task.setDueAt(draft.getNewDueAt());

        task.setTimeInferred(
                Boolean.TRUE.equals(draft.getNewTimeInferred())
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

        if (draft.getUserTimezone() != null
                && !draft.getUserTimezone().isBlank()) {

            task.setUserTimezone(draft.getUserTimezone());
        }

        task.setStatus(resolveStatus(draft));
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

        task.setTimePrecision(
                draft.getNewTimePrecision()
        );

        task.setDueDate(draft.getNewDueDate());
        task.setDueTime(draft.getNewDueTime());
        task.setDueAt(draft.getNewDueAt());

        task.setTimeInferred(
                Boolean.TRUE.equals(draft.getNewTimeInferred())
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

        task.setConfidence(original.getConfidence());

        task.setStatus(resolveStatus(draft));

        task.setRelativeTime(
                Boolean.TRUE.equals(draft.getNewTimeInferred())
        );

        task.setFollowUpCount(0);

        task.setRemindedT24h(false);
        task.setRemindedT6h(false);
        task.setRemindedOverdue(false);

        task.setSourcePlatform(original.getSourcePlatform());

        task.setCreatedAt(now);
        task.setUpdatedAt(now);
    }

    /**
     * 根据截止时间确定任务状态。
     */
    private String resolveStatus(TaskChangeDraft draft) {

        if (draft.getNewDueAt() == null) {
            return "PENDING_TIME";
        }

        if (draft.getNewDueAt().isBefore(LocalDateTime.now())) {
            return "OVERDUE";
        }

        return "ACTIVE";
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
                        .eq(DdlTask::getTaskId, taskId)
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

    /**
     * Entity 转 VO。
     */
    private TaskChangeDraftVO toVO(
            TaskChangeDraft draft) {

        TaskChangeDraftVO vo = new TaskChangeDraftVO();

        vo.setTargetTaskId(draft.getTargetTaskId());
        vo.setNewTaskTitle(draft.getNewTaskTitle());

        vo.setNewTimePrecision(
                draft.getNewTimePrecision()
        );

        vo.setNewDueDate(draft.getNewDueDate());
        vo.setNewDueTime(draft.getNewDueTime());
        vo.setNewDueAt(draft.getNewDueAt());

        vo.setNewTimeInferred(draft.getNewTimeInferred());

        vo.setNewTimeAmbiguityReason(
                draft.getNewTimeAmbiguityReason()
        );

        vo.setNewDeliverableFormat(
                draft.getNewDeliverableFormat()
        );

        vo.setNewSubmissionChannel(
                draft.getNewSubmissionChannel()
        );

        vo.setRawSourceText(draft.getRawSourceText());
        vo.setRawTimeText(draft.getRawTimeText());
        vo.setUserTimezone(draft.getUserTimezone());
        vo.setExpiresAt(draft.getExpiresAt());

        return vo;
    }
}
