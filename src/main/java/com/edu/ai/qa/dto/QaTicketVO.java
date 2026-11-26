package com.edu.ai.qa.dto;

public record QaTicketVO(
        String id,
        String courseId,
        String courseName,
        String studentId,
        String studentName,
        String teacherId,
        String teacherName,
        String sessionId,
        String questionMessageId,
        String questionText,
        String reason,
        String status,
        String teacherReply,
        String createdAt,
        String updatedAt,
        String answeredAt
) {
}
