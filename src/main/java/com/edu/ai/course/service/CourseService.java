
package com.edu.ai.course.service;

import com.edu.ai.course.dto.CourseDTO;
import com.edu.ai.course.entity.Course;

import java.util.List;

public interface CourseService {

    // 创建课程
    Course createCourse(CourseDTO dto, Long userId);

    // 修改自己创建的课程
    void updateCourse(Long courseId, CourseDTO dto, Long userId);

    // 停用自己创建的课程
    void disableCourse(Long courseId, Long userId);

    // 查询当前用户创建的课程
    List<Course> getMyCourses(Long userId);

    // 查看课程详情
    Course getCourseDetail(Long courseId, Long userId);
}
