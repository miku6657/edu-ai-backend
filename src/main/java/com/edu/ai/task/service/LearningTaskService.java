package com.edu.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.entity.CourseStudent;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.course.mapper.CourseStudentMapper;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.enums.UserRole;
import com.edu.ai.system.mapper.UserMapper;

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

@Service
public class LearningTaskService {

    private final LearningTaskMapper taskMapper;
    private final TaskStudentMapper taskStudentMapper;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final CoursePermissionService coursePermissionService;
    private final UserMapper userMapper;

    public LearningTaskService(
            LearningTaskMapper taskMapper,
            TaskStudentMapper taskStudentMapper,
            CourseMapper courseMapper,
            CourseStudentMapper courseStudentMapper,
            CoursePermissionService coursePermissionService,
            UserMapper userMapper) {

        this.taskMapper = taskMapper;
        this.taskStudentMapper = taskStudentMapper;
        this.courseMapper = courseMapper;
        this.courseStudentMapper = courseStudentMapper;
        this.coursePermissionService = coursePermissionService;
        this.userMapper = userMapper;
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

        // 必须是当前课程的有效成员
        coursePermissionService.requireActiveRead(
                task.getCourseId(),
                userId
        );

        User user = userMapper.selectById(userId);

        if (user == null
                || !UserRole.STUDENT.name().equals(user.getRole())) {

            throw new BusinessException(
                    403,
                    "只有学生可以修改课程任务完成状态"
            );
        }

        // 保留原有的原子 Upsert，防止并发重复记录
        taskStudentMapper.upsertCompletion(
                taskId,
                userId,
                completed ? 1 : 0,
                completed ? LocalDateTime.now() : null
        );
    }

    /**
     * 教师查看公共课程任务的全班完成进度。
     *
     * 统计范围：当前仍然加入课程的学生。
     * 没有 task_student 记录的学生视为未完成。
     */
    public Map<String, Object> progress(
            Long taskId,
            Long userId) {

        LearningTask task = requireTask(taskId);

        // 只有课程教师或管理员可以查看全班统计
        requireCourseOwner(task.getCourseId(), userId);

        Long courseId = task.getCourseId();

        // 当前课程的全部学生 ID
        List<Long> studentIds = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .select(CourseStudent::getStudentId)
        ).stream()
         .map(CourseStudent::getStudentId)
         .distinct()
         .toList();

        int totalStudents = studentIds.size();

        long completedStudents = 0;

        if (!studentIds.isEmpty()) {
            completedStudents = taskStudentMapper.selectCount(
                    new LambdaQueryWrapper<TaskStudent>()
                            .eq(TaskStudent::getTaskId, taskId)
                            .eq(TaskStudent::getCompleted, 1)
                            .in(TaskStudent::getStudentId, studentIds)
            );
        }

        long pendingStudents =
                totalStudents - completedStudents;

        double completionRate = totalStudents == 0
                ? 0.0
                : Math.round(
                    completedStudents * 10000.0 / totalStudents
                  ) / 100.0;

        Map<String, Object> result = new HashMap<>();

        result.put("taskId", taskId);
        result.put("courseId", courseId);
        result.put("totalStudents", totalStudents);
        result.put("completedStudents", completedStudents);
        result.put("pendingStudents", pendingStudents);
        result.put("completionRate", completionRate);

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

        coursePermissionService.requireManage(
                courseId,
                userId
        );
    }

    private void requireCourseAccess(
            Long courseId,
            Long userId) {

        coursePermissionService.requireActiveRead(
                courseId,
                userId
        );
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
