package com.edu.ai.document.dto;

import java.util.List;

public final class DocumentQualityRequest {

    private DocumentQualityRequest() {
    }

    /**
     * 创建抽检任务。
     */
    public record CreateTask(

            String title,

            Integer sampleRate

    ) {}

    /**
     * 单条质量问题输入。
     */
    public record IssueInput(

            String issueType,

            String description,

            Long assigneeId

    ) {}

    /**
     * 保存样本检查结果。
     *
     * PASS：检查合格
     * FAIL：检查不合格
     */
    public record CheckSample(

            String result,

            String comment,

            List<IssueInput> issues

    ) {}

    /**
     * 重新分配整改责任人。
     */
    public record AssignIssue(

            Long assigneeId

    ) {}

    /**
     * 提交整改结果。
     */
    public record ResolveIssue(

            String resolution,

            Long replacementDocumentId

    ) {}

    /**
     * 复核整改结果。
     *
     * APPROVE：复核通过
     * REJECT：复核驳回
     */
    public record ReviewIssue(

            String decision,

            String comment

    ) {}
}
