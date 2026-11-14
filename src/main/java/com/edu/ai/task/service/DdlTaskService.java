package com.edu.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.task.dto.CreateDdlTaskRequest;
import com.edu.ai.task.dto.DdlTaskBoardVO;
import com.edu.ai.task.dto.DdlTaskVO;
import com.edu.ai.task.dto.UpdateDdlTaskRequest;
import com.edu.ai.task.entity.DdlTask;
import com.edu.ai.task.mapper.DdlTaskMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
public class DdlTaskService {

    private final DdlTaskMapper taskMapper;

    public DdlTaskService(DdlTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 查询当前用户的全部任务。
     */
    public List<DdlTask> listTasks(
            Long userId,
            String statusGroup,
            Long courseId) {

        LambdaQueryWrapper<DdlTask> wrapper =
                new LambdaQueryWrapper<DdlTask>()
                        .eq(DdlTask::getUserId, userId)
                        .ne(DdlTask::getStatus, "CANCELED");

        if (courseId != null) {
            wrapper.eq(DdlTask::getCourseId, courseId);
        }

        List<DdlTask> tasks = taskMapper.selectList(wrapper);

        LocalDateTime now = LocalDateTime.now();

        // 动态计算逾期状态
        for (DdlTask task : tasks) {
            refreshOverdueStatus(task, now);
        }

        tasks.sort(
                Comparator.comparing(
                        DdlTask::getDueAt,
                        Comparator.nullsLast(
                                Comparator.naturalOrder()
                        )
                )
        );

        return tasks.stream()
                .filter(task -> matchesGroup(
                        task.getStatus(), statusGroup))
                .toList();
    }

    /**
     * 手动创建任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public DdlTask createTask(
            Long userId,
            CreateDdlTaskRequest request) {

        validateCreateRequest(request);

        LocalDateTime now = LocalDateTime.now();

        DdlTask task = new DdlTask();

        task.setUserId(userId);
        task.setCourseId(request.getCourseId());
        task.setCourseName(request.getCourseName().trim());
        task.setTaskTitle(request.getTaskTitle().trim());

        task.setTimePrecision(
                request.getTimePrecision() == null
                        ? "EXACT_DATETIME"
                        : request.getTimePrecision()
        );

        task.setDueDate(request.getDueDate());
        task.setDueTime(request.getDueTime());

        task.setDeliverableFormat(
                request.getDeliverableFormat()
        );

        task.setSubmissionChannel(
                request.getSubmissionChannel()
        );

        task.setTimeInferred(false);
        task.setRelativeTime(false);

        task.setSourcePlatform("MANUAL");

        task.setFollowUpCount(0);

        task.setRemindedT24h(false);
        task.setRemindedT6h(false);
        task.setRemindedOverdue(false);

        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        normalizeTime(task);

        task.setStatus(determineInitialStatus(task, now));

        taskMapper.insert(task);

        return task;
    }

    /**
     * 修改任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public DdlTask updateTask(
            Long taskId,
            Long userId,
            UpdateDdlTaskRequest request) {

        DdlTask task = requireOwnedTask(taskId, userId);

        if ("COMPLETED".equals(task.getStatus())
                || "CANCELED".equals(task.getStatus())) {
            throw new BusinessException(
                    400, "已完成或已取消的任务不能修改");
        }

        validateUpdateRequest(request);

        LocalDate oldDate = task.getDueDate();
        LocalTime oldTime = task.getDueTime();

        task.setCourseId(request.getCourseId());
        task.setCourseName(request.getCourseName().trim());
        task.setTaskTitle(request.getTaskTitle().trim());

        task.setDeliverableFormat(
                request.getDeliverableFormat()
        );

        task.setSubmissionChannel(
                request.getSubmissionChannel()
        );

        task.setTimePrecision(request.getTimePrecision());
        task.setDueDate(request.getDueDate());
        task.setDueTime(request.getDueTime());

        boolean timeChanged =
                !java.util.Objects.equals(
                        oldDate, request.getDueDate())
                || !java.util.Objects.equals(
                        oldTime, request.getDueTime());

        normalizeTime(task);

        if (Boolean.TRUE.equals(request.getReconfirmTime())) {
            task.setStatus(determineInitialStatus(
                    task, LocalDateTime.now()));
        } else if (timeChanged) {
            task.setStatus(determineInitialStatus(
                    task, LocalDateTime.now()));
        }

        task.setUpdatedAt(LocalDateTime.now());

        taskMapper.updateById(task);

        return task;
    }

    /**
     * 标记完成或取消完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public DdlTask setCompletion(
            Long taskId,
            Long userId,
            boolean completed) {

        DdlTask task = requireOwnedTask(taskId, userId);

        if ("CANCELED".equals(task.getStatus())) {
            throw new BusinessException(
                    400, "已取消的任务不能修改完成状态");
        }

        if (completed) {
            task.setStatus("COMPLETED");
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setCompletedAt(null);
            task.setStatus(
                    determineInitialStatus(
                            task, LocalDateTime.now())
            );
        }

        task.setUpdatedAt(LocalDateTime.now());

        taskMapper.updateById(task);

        return task;
    }

    /**
     * 删除任务。
     *
     * 这里使用逻辑删除，不真正删除数据库记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public DdlTask cancelTask(
            Long taskId,
            Long userId) {

        DdlTask task = requireOwnedTask(taskId, userId);

        if ("CANCELED".equals(task.getStatus())) {
            throw new BusinessException(
                    400, "任务已经取消");
        }

        task.setStatus("CANCELED");
        task.setUpdatedAt(LocalDateTime.now());

        taskMapper.updateById(task);

        return task;
    }

    /**
     * 任务看板
     */
    public DdlTaskBoardVO getBoard(
            Long userId,
            Long courseId,
            String statusGroup) {

        List<DdlTask> tasks =
                listTasks(userId, statusGroup, courseId);

        DdlTaskBoardVO board = new DdlTaskBoardVO();

        board.setPendingInboxTasks(
                tasks.stream()
                        .filter(t ->
                                "PENDING_TIME".equals(t.getStatus())
                                || "PENDING_CONFIRM".equals(t.getStatus()))
                        .map(DdlTaskVO::from)
                        .toList()
        );

        board.setActiveTasks(
                tasks.stream()
                        .filter(t ->
                                "ACTIVE".equals(t.getStatus())
                                || "OVERDUE".equals(t.getStatus()))
                        .map(DdlTaskVO::from)
                        .toList()
        );

        board.setCompletedTasks(
                tasks.stream()
                        .filter(t ->
                                "COMPLETED".equals(t.getStatus()))
                        .map(DdlTaskVO::from)
                        .toList()
        );

        // 变更草案暂时由后续的草案查询接口接入
        board.setPendingChangeDrafts(List.of());

        return board;
    }

    /**
     * 查询并校验任务归属。
     */
    public DdlTask requireOwnedTask(
            Long taskId,
            Long userId) {

        if (taskId == null || taskId <= 0) {
            throw new BusinessException(
                    400, "任务ID不合法");
        }

        DdlTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<DdlTask>()
                        .eq(DdlTask::getId, taskId)
                        .eq(DdlTask::getUserId, userId)
                        .last("LIMIT 1")
        );

        if (task == null) {
            throw new BusinessException(
                    404, "任务不存在或无权访问");
        }

        return task;
    }

    /**
     * 根据日期和时间生成 dueAt。
     */
    private void normalizeTime(DdlTask task) {

        if (task.getDueDate() == null) {
            task.setDueAt(null);
            return;
        }

        if (task.getDueTime() == null) {
            task.setDueAt(null);
            return;
        }

        task.setDueAt(
                LocalDateTime.of(
                        task.getDueDate(),
                        task.getDueTime()
                )
        );
    }

    /**
     * 确定任务初始状态。
     */
    private String determineInitialStatus(
            DdlTask task,
            LocalDateTime now) {

        String precision = task.getTimePrecision();

        if ("VAGUE_OR_MISSING".equals(precision)
                || "PENDING_CONFIRM".equals(precision)) {
            return "PENDING_CONFIRM";
        }

        if (task.getDueDate() == null) {
            return "PENDING_TIME";
        }

        if (task.getDueAt() == null) {
            return "PENDING_TIME";
        }

        if (task.getDueAt().isBefore(now)) {
            return "PENDING_CONFIRM";
        }

        return "ACTIVE";
    }

    /**
     * 动态刷新逾期状态。
     */
    private void refreshOverdueStatus(
            DdlTask task,
            LocalDateTime now) {

        if (!"ACTIVE".equals(task.getStatus())) {
            return;
        }

        if (task.getDueAt() != null
                && task.getDueAt().isBefore(now)) {

            task.setStatus("OVERDUE");
            task.setUpdatedAt(now);

            taskMapper.updateById(task);
        }
    }

    private boolean matchesGroup(
            String status,
            String group) {

        if (group == null || "ALL".equals(group)) {
            return true;
        }

        return switch (group) {
            case "PENDING_INBOX" ->
                    "PENDING_TIME".equals(status)
                    || "PENDING_CONFIRM".equals(status);

            case "ACTIVE_BOARD" ->
                    "ACTIVE".equals(status)
                    || "OVERDUE".equals(status);

            case "COMPLETED" ->
                    "COMPLETED".equals(status);

            default -> throw new BusinessException(
                    400, "不支持的任务状态分组");
        };
    }

    private void validateCreateRequest(
            CreateDdlTaskRequest request) {

        if (request == null) {
            throw new BusinessException(
                    400, "请求参数不能为空");
        }

        if (request.getClientRequestId() == null
                || request.getClientRequestId().isBlank()) {
            throw new BusinessException(
                    400, "请求ID不能为空");
        }

        if (request.getCourseName() == null
                || request.getCourseName().isBlank()) {
            throw new BusinessException(
                    400, "课程名称不能为空");
        }

        if (request.getTaskTitle() == null
                || request.getTaskTitle().isBlank()) {
            throw new BusinessException(
                    400, "任务标题不能为空");
        }

        if (request.getCourseName().length() > 100) {
            throw new BusinessException(
                    400, "课程名称过长");
        }

        if (request.getTaskTitle().length() > 200) {
            throw new BusinessException(
                    400, "任务标题不能超过200个字符");
        }

        String precision = request.getTimePrecision();

        if (precision != null
                && !List.of(
                        "EXACT_DATETIME",
                        "DATE_ONLY",
                        "VAGUE_OR_MISSING"
                ).contains(precision)) {
            throw new BusinessException(
                    400, "不支持的时间精度");
        }
    }

    private void validateUpdateRequest(
            UpdateDdlTaskRequest request) {

        if (request == null
                || request.getCourseName() == null
                || request.getCourseName().isBlank()
                || request.getTaskTitle() == null
                || request.getTaskTitle().isBlank()) {

            throw new BusinessException(
                    400, "课程名称和任务标题不能为空");
        }

        if (request.getTaskTitle().length() > 200
                || request.getCourseName().length() > 100) {
            throw new BusinessException(
                    400, "课程名称或任务标题过长");
        }

        if (!List.of(
                "EXACT_DATETIME",
                "DATE_ONLY",
                "VAGUE_OR_MISSING"
        ).contains(request.getTimePrecision())) {
            throw new BusinessException(
                    400, "不支持的时间精度");
        }
    }
}
