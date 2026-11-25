package com.edu.ai.course.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CourseDTO {

    /**
     * 课程编码，例如 JAVA101
     */
    private String courseCode;

    /**
     * 课程名称
     */
    private String name;

    /**
     * 课程描述
     */
    private String description;

    /**
     * 课程学分
     */
    private BigDecimal credits;

    /**
     * 适用专业
     */
    private String applicableMajor;

    /**
     * 开课学期
     */
    private String semester;
}
