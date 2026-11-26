package com.edu.ai.chat.dto;

public record QuestionAnalyticsSummaryVO(
        long totalQuestions,
        long participatingStudents,
        long mappedQuestions,
        long unclassifiedQuestions,
        long pendingTickets,
        long answeredTickets
) {
}
