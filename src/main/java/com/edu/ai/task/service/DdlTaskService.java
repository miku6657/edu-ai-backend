package com.edu.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;

import com.edu.ai.task.dto.DdlTaskCreateRequest;
import com.edu.ai.task.dto.DdlTaskUpdateRequest;
import com.edu.ai.task.dto.DdlTaskVO;

import com.edu.ai.task.entity.DdlTask;
import com.edu.ai.task.mapper.DdlTaskMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DdlTaskService {

    private final DdlTaskMapper ddlTaskMapper;
    private final CourseMapper courseMapper;

    public DdlTaskService(
            DdlTaskMapper ddlTaskMapper,
            CourseMapper courseMapper) {

        this.ddlTaskMapper = ddlTaskMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 创建个人 DDL 任务
     */
    @Transactional(rollbackFor = Exception.class)
    public DdlTaskVO create(
            Long userId,
            DdlTaskCreateRequest request) {

        validateUser(userId);
        validateCreateRequest(request);

        // 如果关联课程，检查当前用户是否有权限
        if (request.getCourseId() != null) {
            Course course = requireOwnedCourse(
                    request.getCourseId(), userId);

            request.setCourseName(course.getName());
        }

        DdlTask task = new DdlTask();

        task.setUserId(userId);

        task.setDisplayCode(
                "DDL-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 12)
                        .toUpperCase()
        );

        task.setCourseId(request.getCourseId());
        task.setCourseName(
                request.getCourseName() == null
                        ? "未关联课程"
                        : request.getCourseName()
        );

        task.setTaskTitle(request.getTaskTitle().trim());

        task.setDeliverableFormat(
                request.getDeliverableFormat());

        task.setSubmissionChannel(
                request.getSubmissionChannel());

        task.setRawSourceText(request.getRawSourceText());
        task.setRawTimeText(request.getRawTimeText());

        task.setTimePrecision(
                normalizePrecision(request.getTimePrecision()));

        task.setRelativeTime(
                Boolean.TRUE.equals(request.getRelativeTime()));

        task.setDueDate(request.getDueDate());
        task.setDueTime(request.getDueTime());
        task.setDueAt(request.getDueAt());

        task.setTimeInferred(
                Boolean.TRUE.equals(request.getTimeInferred()));

        task.setTimeAmbiguityReason(
                request.getTimeAmbiguityReason());

        task.setUserTimezone(
                request.getUserTimezone() == null
                        ? "Asia/Shanghai"
                        : request.getUserTimezone());

        task.setStatus(resolveInitialStatus(task));

        task.setConfidence(null);

        task.setFollowUpCount(0);

        task.setRemindedT24h(false);
        task.setRemindedT6h(false);
        task.setRemindedOverdue(false);

        task.setSourcePlatform(
                request.getSourcePlatform() == null
                        ? "MANUAL"
                        : request.getSourcePlatform());

        LocalDateTime now = LocalDateTime.now();

        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        ddlTaskMapper.insert(task);

        return DdlTaskVO.from(task);
    }

    /**
     * 查询当前用户全部任务
     */
    public List<DdlTaskVO> list(Long userId) {

        validateUser(userId);

        List<DdlTask> tasks = ddlTaskMapper.selectList(
                new LambdaQueryWrapper<DdlTask>()
                        .eq(DdlTask::getUserId, userId)
                        .orderByAsc(DdlTask::getDueAt)
                        .orderByDesc(DdlTask::getCreatedAt)
        );

        return tasks.stream()
                .map(DdlTaskVO::from)
                .toList();
    }

    /**
     * 查询单个任务
     */
    public DdlTaskVO detail(
            Long taskId,
            Long userId) {

        DdlTask task = requireUserTask(taskId, userId);

        return DdlTaskVO.from(task);
    }

    /**
     * 修改个人任务
     */
    @Transactional(rollbackFor = Exception.class)
    public DdlTaskVO update(
            Long taskId,
            Long userId,
            DdlTaskUpdateRequest request) {

        validateUser(userId);

        if (request == null
                || request.getTaskTitle() == null
                || request.getTaskTitle().isBlank()) {

            throw new BusinessException(
                    400, "任务标题不能为空");
        }

        DdlTask task = requireUserTask(taskId, userId);

        if ("COMPLETED".equals(task.getStatus())
                || "CANCELED".equals(task.getStatus())) {

            throw new BusinessException(
                    400, "已完成或已取消的任务不能修改");
        }

        if (request.getCourseId() != null) {

            Course course = requireOwnedCourse(
                    request.getCourseId(), userId);

            task.setCourseId(course.getId());
            task.setCourseName(course.getName());

        } else {
            task.setCourseId(null);
            task.setCourseName("未关联课程");
        }

        task.setTaskTitle(request.getTaskTitle().trim());

        task.setDeliverableFormat(
                request.getDeliverableFormat());

        task.setSubmissionChannel(
                request.getSubmissionChannel());

        task.setTimePrecision(
                normalizePrecision(request.getTimePrecision()));

        task.setDueDate(request.getDueDate());
        task.setDueTime(request.getDueTime());
        task.setDueAt(request.getDueAt());

        task.setTimeInferred(
                Boolean.TRUE.equals(request.getTimeInferred()));

        task.setTimeAmbiguityReason(
                request.getTimeAmbiguityReason());

        if (request.getUserTimezone() != null) {
            task.setUserTimezone(request.getUserTimezone());
        }

        task.setStatus(resolveInitialStatus(task));

        task.setUpdatedAt(LocalDateTime.now());

        ddlTaskMapper.updateById(task);

        return DdlTaskVO.from(task);
    }

    /**
     * 完成任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void complete(
            Long taskId,
            Long userId) {

        DdlTask task = requireUserTask(taskId, userId);

        if ("CANCELED".equals(task.getStatus())) {
            throw new BusinessException(
                    400, "已取消的任务不能完成");
        }

        if ("PENDING_TIME".equals(task.getStatus())
                || "PENDING_CONFIRM".equals(task.getStatus())) {

            throw new BusinessException(
                    400, "请先确认任务截止时间");
        }

        task.setStatus("COMPLETED");
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        ddlTaskMapper.updateById(task);
    }

    /**
     * 取消任务
     *
     * 使用逻辑取消，不物理删除数据库记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(
            Long taskId,
            Long userId) {

        DdlTask task = requireUserTask(taskId, userId);

        if ("COMPLETED".equals(task.getStatus())) {
            throw new BusinessException(
                    400, "已完成的任务不能取消");
        }

        task.setStatus("CANCELED");
        task.setUpdatedAt(LocalDateTime.now());

        ddlTaskMapper.updateById(task);
    }

    /**
     * 撤销完成
     */
    @Transactional(rollbackFor = Exception.class)
    public void reopen(
            Long taskId,
            Long userId) {

        DdlTask task = requireUserTask(taskId, userId);

        if (!"COMPLETED".equals(task.getStatus())) {
            throw new BusinessException(
                    400, "只有已完成的任务可以撤销完成");
        }

        task.setCompletedAt(null);
        task.setStatus(resolveInitialStatus(task));
        task.setUpdatedAt(LocalDateTime.now());

        ddlTaskMapper.updateById(task);
    }

    /**
     * 按用户归属查询任务
     */
    private DdlTask requireUserTask(
            Long taskId,
            Long userId) {

        validateUser(userId);

        DdlTask task = ddlTaskMapper.selectOne(
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
     * 校验课程归属
     */
    private Course requireOwnedCourse(
            Long courseId,
            Long userId) {

        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            throw new BusinessException(
                    404, "关联课程不存在");
        }

        if (!userId.equals(course.getOwnerId())) {
            throw new BusinessException(
                    403, "只能关联自己创建的课程");
        }

        if (!Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(
                    403, "关联课程已停用");
        }

        return course;
    }

    private void validateUser(Long userId) {

        if (userId == null || userId <= 0) {
            throw new BusinessException(
                    401, "用户身份无效");
        }
    }

    private void validateCreateRequest(
            DdlTaskCreateRequest request) {

        if (request == null
                || request.getTaskTitle() == null
                || request.getTaskTitle().isBlank()) {

            throw new BusinessException(
                    400, "任务标题不能为空");
        }

        if (request.getTaskTitle().trim().length() > 200) {
            throw new BusinessException(
                    400, "任务标题不能超过200个字符");
        }
    }

    /**
     * 统一时间精度
     */
    private String normalizePrecision(String precision) {

        if (precision == null || precision.isBlank()) {
            return "VAGUE_OR_MISSING";
        }

        return switch (precision) {
            case "EXACT_DATETIME",
                 "DATE_ONLY",
                 "VAGUE_OR_MISSING",
                 "INVALID_OR_PAST" -> precision;

            default -> throw new BusinessException(
                    400, "不支持的时间精度");
        };
    }

    /**
     * 确定任务初始状态
     */
    private String resolveInitialStatus(DdlTask task) {

        String precision = task.getTimePrecision();

        if ("VAGUE_OR_MISSING".equals(precision)
                || task.getDueDate() == null) {

            return "PENDING_TIME";
        }

        if ("INVALID_OR_PAST".equals(precision)) {
            return "PENDING_CONFIRM";
        }

        if (Boolean.TRUE.equals(task.getTimeInferred())
                || (task.getTimeAmbiguityReason() != null
                && !task.getTimeAmbiguityReason().isBlank())) {

            return "PENDING_CONFIRM";
        }

        if (task.getDueAt() != null
                && task.getDueAt().isBefore(LocalDateTime.now())) {

            return "PENDING_CONFIRM";
        }

        return "ACTIVE";
    }
}
