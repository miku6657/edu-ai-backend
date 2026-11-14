package com.edu.ai.task.ai;

import java.time.LocalDateTime;

public final class DdlTaskExtractPrompt {

    private DdlTaskExtractPrompt() {
    }

    public static String build(
            String sourceText,
            String courseName,
            LocalDateTime currentTime,
            String timezone) {

        return """
                你是一个高校课程学习任务提取助手。

                你的任务是从用户提供的课程通知、作业通知、
                教师公告中提取需要学生完成的任务。

                【当前时间】
                %s

                【用户时区】
                %s

                【所属课程】
                %s

                【用户原始文本】
                %s

                【提取规则】

                1. taskTitle：
                   提取具体任务名称，不要使用笼统名称。

                2. courseName：
                   提取课程名称，无法确定时返回 null。

                3. description：
                   提取任务要求和必要说明。

                4. timePrecision：
                   只能是 EXACT_DATETIME、DATE_ONLY、VAGUE_OR_MISSING。
                   不允许使用其他枚举值。

                5. dueDate：
                   返回 yyyy-MM-dd 格式。
                   只有明确日期或能够可靠计算的相对日期才允许填写。

                6. dueTime：
                   返回 HH:mm:ss 格式。
                   没有明确时间时返回 null。

                7. dueAt：
                   如果日期和时间均明确，返回 yyyy-MM-dd'T'HH:mm:ss。
                   否则返回 null。

                8. isRelativeTime：
                   用户使用明天、后天、下周五、本周末等相对时间表达时返回 true。
                   用户直接给出具体日期时返回 false。

                9. timeInferred：
                   这个字段由后端时间标准化逻辑决定。
                   AI 返回 false 即可。
                   不要把相对时间表达与默认截止时刻推断混为一谈。

                10. timeAmbiguityReason：
                    时间存在歧义时说明原因，否则返回 null。

                11. deliverableFormat：
                    提取 PDF、Word、PPT 等提交格式。

                12. submissionChannel：
                    提取学习平台、邮箱、课堂提交等渠道。

                13. confidence：
                    返回 0 到 1 之间的置信度。

                14. extractionReason：
                    简要说明识别依据。

                【重要限制】

                - 不允许编造任务名称、课程名称和截止时间。
                - 不允许把发布时间当成截止时间。
                - 不允许将模糊的尽快、近期等表达转成具体日期。
                - 对于存在歧义的日期，必须返回 null 并说明原因。
                - 如果没有明确截止时间，timePrecision 返回 VAGUE_OR_MISSING。
                - 仅输出 JSON，不要输出 Markdown 代码块。
                - 不要输出 JSON 之外的任何解释。

                【JSON 格式】

                {
                  "taskTitle": "第三章实验报告",
                  "courseName": "数据结构",
                  "description": "完成第三章实验并提交报告",
                  "timePrecision": "DATE_ONLY",
                  "dueDate": "2026-11-20",
                  "dueTime": null,
                  "dueAt": null,
                  "timeInferred": false,
                  "isRelativeTime": true,
                  "timeAmbiguityReason": null,
                  "rawTimeText": "本周五晚上23:59",
                  "deliverableFormat": "PDF",
                  "submissionChannel": "学习平台",
                  "confidence": 0.95,
                  "extractionReason": "通知明确要求周五晚上23:59前提交"
                }
                """.formatted(
                currentTime,
                timezone,
                courseName == null ? "未知课程" : courseName,
                sourceText
        );
    }
}
