package com.edu.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.entity.CourseStudent;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.course.mapper.CourseStudentMapper;
import com.edu.ai.task.dto.TaskReminderVO;
import com.edu.ai.task.dto.TaskReminderSummaryVO;
import com.edu.ai.task.entity.LearningTask;
import com.edu.ai.task.entity.TaskStudent;
import com.edu.ai.task.mapper.LearningTaskMapper;
import com.edu.ai.task.mapper.TaskStudentMapper;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskReminderService {

    private static final long DUE_SOON_HOURS = 24;

    private final CourseStudentMapper courseStudentMapper;
    private final CourseMapper courseMapper;
    private final LearningTaskMapper taskMapper;
    private final TaskStudentMapper taskStudentMapper;

    public TaskReminderService(
            CourseStudentMapper courseStudentMapper,
            CourseMapper courseMapper,
            LearningTaskMapper taskMapper,
            TaskStudentMapper taskStudentMapper) {

        this.courseStudentMapper = courseStudentMapper;
        this.courseMapper = courseMapper;
        this.taskMapper = taskMapper;
        this.taskStudentMapper = taskStudentMapper;
    }

    public TaskReminderSummaryVO getReminders(Long studentId) {

        TaskReminderSummaryVO summary =
                new TaskReminderSummaryVO();

        summary.setTotalPending(0);
        summary.setDueSoonCount(0);
        summary.setOverdueCount(0);
        summary.setReminders(List.of());

        // 1. 查询学生已加入的课程
        List<CourseStudent> enrollments =
                courseStudentMapper.selectList(
                        new LambdaQueryWrapper<CourseStudent>()
                                .eq(CourseStudent::getStudentId,
                                        studentId)
                );

        if (enrollments.isEmpty()) {
            return summary;
        }

        List<Long> courseIds = enrollments.stream()
                .map(CourseStudent::getCourseId)
                .distinct()
                .toList();

        // 2. 只查询仍然开放的课程
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .in(Course::getId, courseIds)
                        .eq(Course::getStatus, 1)
        );

        if (courses.isEmpty()) {
            return summary;
        }

        Map<Long, String> courseNames = courses.stream()
                .collect(Collectors.toMap(
                        Course::getId,
                        Course::getName
                ));

        // 3. 查询这些课程中的有效任务
        List<LearningTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<LearningTask>()
                        .in(LearningTask::getCourseId,
                                courseNames.keySet())
                        .eq(LearningTask::getStatus, 1)
                        .orderByAsc(LearningTask::getDeadline)
        );

        if (tasks.isEmpty()) {
            return summary;
        }

        List<Long> taskIds = tasks.stream()
                .map(LearningTask::getId)
                .toList();

        // 4. 查询该学生已完成的任务 ID
        List<TaskStudent> completedRecords =
                taskStudentMapper.selectList(
                        new LambdaQueryWrapper<TaskStudent>()
                                .eq(TaskStudent::getStudentId,
                                        studentId)
                                .eq(TaskStudent::getCompleted, 1)
                                .in(TaskStudent::getTaskId,
                                        taskIds)
                );

        Set<Long> completedTaskIds =
                completedRecords.stream()
                        .map(TaskStudent::getTaskId)
                        .collect(Collectors.toSet());

        // 5. 按截止时间实时计算提醒
        LocalDateTime now = LocalDateTime.now();

        int dueSoonCount = 0;
        int overdueCount = 0;

        List<TaskReminderVO> reminders = new ArrayList<>();

        for (LearningTask task : tasks) {

            if (completedTaskIds.contains(task.getId())) {
                continue;
            }

            if (task.getDeadline() == null) {
                continue;
            }

            TaskReminderVO vo = new TaskReminderVO();

            vo.setTaskId(task.getId());
            vo.setCourseId(task.getCourseId());
            vo.setCourseName(
                    courseNames.get(task.getCourseId())
            );
            vo.setTitle(task.getTitle());
            vo.setDescription(task.getDescription());
            vo.setDeadline(task.getDeadline());

            Duration remaining = Duration.between(
                    now,
                    task.getDeadline()
            );

            vo.setRemainingMinutes(remaining.toMinutes());

            if (remaining.isNegative() || remaining.isZero()) {

                vo.setReminderType("OVERDUE");
                overdueCount++;

            } else if (remaining.compareTo(
                    Duration.ofHours(DUE_SOON_HOURS)) <= 0) {

                vo.setReminderType("DUE_SOON");
                dueSoonCount++;

            } else {

                vo.setReminderType("NORMAL");
            }

            reminders.add(vo);
        }

        // 6. 逾期任务优先，其次临近截止
        Map<String, Integer> priority = Map.of(
                "OVERDUE", 0,
                "DUE_SOON", 1,
                "NORMAL", 2
        );

        reminders.sort(
                Comparator.comparingInt(
                        (TaskReminderVO item) ->
                                priority.get(item.getReminderType())
                ).thenComparing(TaskReminderVO::getDeadline)
        );

        summary.setTotalPending(reminders.size());
        summary.setDueSoonCount(dueSoonCount);
        summary.setOverdueCount(overdueCount);
        summary.setReminders(reminders);

        return summary;
    }
}
