package com.edu.ai.task.dto;

import java.time.LocalDateTime;

/**
 * F6-02 学生知识点综合诊断结果。
 */
public record StudentWeakPointDiagnosticVO(

        String pointId,

        String title,

        // 最近30天有效提问次数
        int effectiveQuestionCount,

        // 涉及会话数
        int questionSessionCount,

        // 提问推断等级：0薄弱、1疑似不熟练、2暂无明显薄弱证据
        int questionInferredLevel,

        // 最近练习统计
        int practiceQuestionCount,

        int masteredQuestionCount,

        // 练习掌握率，没有练习记录时为null
        Integer practiceMasteryRate,

        // 学生手动自评：0-3
        int selfAssessmentLevel,

        // 练习推断等级，没有练习记录时为null
        Integer practiceLevel,

        // 综合诊断：0重点巩固、1建议加强、2暂无明显薄弱证据
        int diagnosisLevel,

        String diagnosisLabel,

        // 诊断依据
        String diagnosisReason,

        // 改进建议
        String suggestion,

        LocalDateTime lastPracticedAt

) {}
