package com.edu.ai.admin.dto;

import com.edu.ai.course.entity.Course;
import lombok.Data;

import java.util.List;

@Data
public class CoursePageVO {

    private Long current;

    private Long size;

    private Long total;

    private Long pages;

    private List<Course> records;
}
