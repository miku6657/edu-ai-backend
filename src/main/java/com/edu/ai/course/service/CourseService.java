package com.edu.ai.course.service;

import com.edu.ai.course.dto.CourseDTO;
import com.edu.ai.course.entity.Course;

import java.util.List;

public interface CourseService {

    Course createCourse(CourseDTO dto, Long userId);

    void updateCourse(Long courseId, CourseDTO dto, Long userId);

    void disableCourse(Long courseId, Long userId);

    // 当前用户创建或加入的课程
    List<Course> getUserCourses(Long userId);

    // 当前用户可以加入的课程
    List<Course> getAvailableCourses(Long userId);

    // 课程详情
    Course getCourseDetail(Long courseId, Long userId);

    // 加入课程
    void joinCourse(Long courseId, Long userId);

    // 退出课程
    void leaveCourse(Long courseId, Long userId);
}
