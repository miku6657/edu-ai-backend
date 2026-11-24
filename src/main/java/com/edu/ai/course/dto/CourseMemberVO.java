package com.edu.ai.course.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseMemberVO {

    private String studentId;

    private String username;

    private String nickname;

    private LocalDateTime joinTime;
}
