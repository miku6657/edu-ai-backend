package com.edu.ai.chat.dto;

public record TeacherQuestionRecordVO(
        String messageId,
        String sessionId,
        String studentId,
        String studentName,
        String questionText,
        String askedAt,
        String knowledgePointId,
        String knowledgePointTitle,
        String handlingStatus,
        String ticketId,
        String ticketStatus,
        String noEvidenceReason,
        String teacherReply,
        String aiAnswerPreview
) {
}
