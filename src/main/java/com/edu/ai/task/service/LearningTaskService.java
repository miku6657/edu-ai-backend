package com.edu.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.entity.CourseStudent;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.course.mapper.CourseStudentMapper;

import com.edu.ai.task.dto.LearningTaskRequest;
import com.edu.ai.task.dto.StudentTaskVO;
import com.edu.ai.task.entity.LearningTask;
import com.edu.ai.task.entity.TaskStudent;
import com.edu.ai.task.mapper.LearningTaskMapper;
import com.edu.ai.task.mapper.TaskStudentMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LearningTaskService {

    private final LearningTaskMapper taskMapper;
    private final TaskStudentMapper taskStudentMapper;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;

    public LearningTaskService(
            LearningTaskMapper taskMapper,
            TaskStudentMapper taskStudentMapper,
            CourseMapper courseMapper,
            CourseStudentMapper courseStudentMapper) {

        this.taskMapper = taskMapper;
        this.taskStudentMapper = taskStudentMapper;
        this.courseMapper = courseMapper;
        this.courseStudentMapper = courseStudentMapper;
    }

    // 创建课程任务
    @Transactional(rollbackFor = Exception.class)
    public LearningTask create(
            Long courseId,
            Long userId,
            LearningTaskRequest request) {

        requireCourseOwner(courseId, userId);
        validateRequest(request);

        LocalDateTime now = LocalDateTime.now();

        LearningTask task = new LearningTask();
        task.setCourseId(courseId);
        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setDeadline(request.getDeadline());
        task.setStatus(1);
        task.setCreateTime(now);
        task.setUpdateTime(now);

        taskMapper.insert(task);

        return task;
    }

    // 修改任务
    @Transactional(rollbackFor = Exception.class)
    public void update(
            Long taskId,
            Long userId,
            LearningTaskRequest request) {

        LearningTask task = requireTask(taskId);
        requireCourseOwner(task.getCourseId(), userId);
        requireActiveTask(task);
        validateRequest(request);

        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription());
        task.setDeadline(request.getDeadline());
        task.setUpdateTime(LocalDateTime.now());

        taskMapper.updateById(task);
    }

    // 取消任务
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long taskId, Long userId) {

        LearningTask task = requireTask(taskId);
        requireCourseOwner(task.getCourseId(), userId);

        taskMapper.update(
                null,
                new LambdaUpdateWrapper<LearningTask>()
                        .eq(LearningTask::getId, taskId)
                        .set(LearningTask::getStatus, 0)
                        .set(
                                LearningTask::getUpdateTime,
                                LocalDateTime.now()
                        )
        );
    }

    /**
     * 查询当前用户有权访问的课程任务，
     * 同时返回当前用户自己的完成状态。
     */
    public List<StudentTaskVO> listForUser(
            Long courseId,
            Long userId) {

        requireCourseAccess(courseId, userId);

        List<LearningTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<LearningTask>()
                        .eq(LearningTask::getCourseId, courseId)
                        .eq(LearningTask::getStatus, 1)
                        .orderByAsc(LearningTask::getDeadline)
        );

        if (tasks.isEmpty()) {
            return List.of();
        }

        List<Long> taskIds = tasks.stream()
                .map(LearningTask::getId)
                .toList();

        // 查询当前用户的完成记录
        List<TaskStudent> records =
                taskStudentMapper.selectList(
                        new LambdaQueryWrapper<TaskStudent>()
                                .eq(TaskStudent::getStudentId, userId)
                                .in(TaskStudent::getTaskId, taskIds)
                );

        Map<Long, TaskStudent> recordMap = new HashMap<>();

        for (TaskStudent record : records) {
            recordMap.put(record.getTaskId(), record);
        }

        LocalDateTime now = LocalDateTime.now();

        return tasks.stream().map(task -> {

            TaskStudent record = recordMap.get(task.getId());

            boolean completed = record != null
                    && Integer.valueOf(1)
                            .equals(record.getCompleted());

            return StudentTaskVO.of(
                    task,
                    completed,
                    record == null ? null : record.getCompletedTime(),
                    now
            );

        }).toList();
    }

    // 修改任务完成状态
    @Transactional(rollbackFor = Exception.class)
    public void setCompleted(
            Long taskId,
            Long userId,
            boolean completed) {

        LearningTask task = requireTask(taskId);
        requireActiveTask(task);

        // 课程创建者或已加入课程的用户均可操作
        requireCourseAccess(task.getCourseId(), userId);

        // 使用 MySQL 原子 upsert，避免并发产生重复记录
        taskStudentMapper.upsertCompletion(
                taskId,
                userId,
                completed ? 1 : 0,
                completed ? LocalDateTime.now() : null
        );
    }

    // 查看任务完成统计（仅课程创建者）
    public Map<String, Object> progress(
            Long taskId,
            Long userId) {

        LearningTask task = requireTask(taskId);
        requireCourseOwner(task.getCourseId(), userId);

        List<CourseStudent> members =
                courseStudentMapper.selectList(
                        new LambdaQueryWrapper<CourseStudent>()
                                .eq(
                                        CourseStudent::getCourseId,
                                        task.getCourseId()
                                )
                );

        Set<Long> memberIds = members.stream()
                .map(CourseStudent::getStudentId)
                .collect(Collectors.toSet());

        long completedCount = 0;

        if (!memberIds.isEmpty()) {
            completedCount = taskStudentMapper.selectCount(
                    new LambdaQueryWrapper<TaskStudent>()
                            .eq(TaskStudent::getTaskId, taskId)
                            .eq(TaskStudent::getCompleted, 1)
                            .in(TaskStudent::getStudentId, memberIds)
            );
        }

        Map<String, Object> result = new HashMap<>();

        result.put("taskId", taskId);
        result.put("totalStudents", memberIds.size());
        result.put("completedStudents", completedCount);
        result.put(
                "uncompletedStudents",
                memberIds.size() - completedCount
        );

        return result;
    }

    private Course requireCourse(Long courseId) {

        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        if (!Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(403, "课程已停用");
        }

        return course;
    }

    private LearningTask requireTask(Long taskId) {

        LearningTask task = taskMapper.selectById(taskId);

        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        return task;
    }

    private void requireActiveTask(LearningTask task) {

        if (!Integer.valueOf(1).equals(task.getStatus())) {
            throw new BusinessException(400, "该任务已经取消");
        }
    }

    private void requireCourseOwner(
            Long courseId,
            Long userId) {

        Course course = requireCourse(courseId);

        if (!userId.equals(course.getOwnerId())) {
            throw new BusinessException(
                    403, "只有课程创建者可以管理任务");
        }
    }

    /**
     * 课程创建者或已经加入课程的用户可以访问。
     */
    private void requireCourseAccess(
            Long courseId,
            Long userId) {

        Course course = requireCourse(courseId);

        // 创建者直接放行
        if (userId.equals(course.getOwnerId())) {
            return;
        }

        // 检查当前用户是否加入课程
        Long count = courseStudentMapper.selectCount(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStudentId, userId)
        );

        if (count == 0) {
            throw new BusinessException(
                    403, "无权访问该课程任务");
        }
    }

    private void validateRequest(LearningTaskRequest request) {

        if (request == null
                || request.getTitle() == null
                || request.getTitle().isBlank()) {
            throw new BusinessException(400, "任务标题不能为空");
        }

        if (request.getTitle().trim().length() > 100) {
            throw new BusinessException(
                    400, "任务标题不能超过100个字符");
        }

        if (request.getDescription() != null
                && request.getDescription().length() > 10000) {
            throw new BusinessException(
                    400, "任务描述过长");
        }

        if (request.getDeadline() == null) {
            throw new BusinessException(
                    400, "请填写截止时间");
        }

        if (!request.getDeadline().isAfter(
                LocalDateTime.now())) {
            throw new BusinessException(
                    400, "截止时间必须晚于当前时间");
        }
    }
}
