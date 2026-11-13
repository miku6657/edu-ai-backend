package com.edu.ai.course.service;

import com.edu.ai.course.dto.CourseDTO;
import com.edu.ai.course.entity.Course;

import java.util.List;

public interface CourseService {

    // 负责人创建课程
    Course createCourse(CourseDTO dto, Long ownerId);

    // 负责人修改自己的课程
    void updateCourse(
            Long courseId, CourseDTO dto, Long ownerId);

    // 停用课程
    void disableCourse(Long courseId, Long ownerId);

    // 查询当前负责人创建的课程
    List<Course> getOwnerCourses(Long ownerId);

    // 查询当前学生加入的课程
    List<Course> getStudentCourses(Long studentId);

    // 查询所有可加入课程
    List<Course> getAvailableCourses();

    // 查看课程详情
    Course getCourseDetail(
            Long courseId, Long userId, String role);

    // 学生加入课程
    void joinCourse(Long courseId, Long studentId);

    // 学生退出课程
    void leaveCourse(Long courseId, Long studentId);
}
