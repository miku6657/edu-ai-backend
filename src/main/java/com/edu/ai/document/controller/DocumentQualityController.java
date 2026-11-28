package com.edu.ai.document.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.document.dto.DocumentQualityRequest;
import com.edu.ai.document.dto.DocumentQualityVO;

import com.edu.ai.document.service.DocumentQualityScanService;
import com.edu.ai.document.service.DocumentQualityService;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/v1/document-quality/courses/{courseId}"
)
public class DocumentQualityController {

    private final DocumentQualityService service;

    private final DocumentQualityScanService scanService;

    public DocumentQualityController(
            DocumentQualityService service,
            DocumentQualityScanService scanService) {

        this.service = service;
        this.scanService = scanService;
    }

    private Long userId(
            Authentication auth) {

        if (auth == null
                || !(auth.getPrincipal()
                        instanceof Long id)) {

            throw new BusinessException(

                    401,

                    "登录状态无效"
            );
        }

        return id;
    }

    /**
     * 查询可分配的整改责任人。
     */
    @GetMapping("/assignees")
    public Result<List<DocumentQualityVO.Assignee>>
            listAssignees(

            @PathVariable Long courseId,

            Authentication auth) {

        return Result.success(

                service.listAssignees(

                        courseId,

                        userId(auth)
                )
        );
    }

    /**
     * 创建抽检任务。
     */
    @PostMapping("/documents/{documentId}/tasks")
    public Result<DocumentQualityVO.Detail>
            createTask(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody
            DocumentQualityRequest.CreateTask request,

            Authentication auth) {

        return Result.success(

                service.createTask(

                        courseId,

                        documentId,

                        userId(auth),

                        request
                )
        );
    }

    /**
     * 查询课程或指定文档的抽检任务。
     */
    @GetMapping("/tasks")
    public Result<List<DocumentQualityVO.Task>>
            listTasks(

            @PathVariable Long courseId,

            @RequestParam(required = false)
            Long documentId,

            Authentication auth) {

        return Result.success(

                service.listTasks(

                        courseId,

                        documentId,

                        userId(auth)
                )
        );
    }

    /**
     * 查询抽检任务详情。
     */
    @GetMapping("/tasks/{taskId}")
    public Result<DocumentQualityVO.Detail>
            getDetail(

            @PathVariable Long courseId,

            @PathVariable Long taskId,

            Authentication auth) {

        return Result.success(

                service.getDetail(

                        courseId,

                        taskId,

                        userId(auth)
                )
        );
    }

    /**
     * 保存样本检查结果。
     */
    @PostMapping(
            "/tasks/{taskId}"
            + "/samples/{sampleId}/check"
    )
    public Result<DocumentQualityVO.Detail>
            checkSample(

            @PathVariable Long courseId,

            @PathVariable Long taskId,

            @PathVariable Long sampleId,

            @RequestBody
            DocumentQualityRequest.CheckSample request,

            Authentication auth) {

        return Result.success(

                service.checkSample(

                        courseId,

                        taskId,

                        sampleId,

                        userId(auth),

                        request
                )
        );
    }

    /**
     * 重新分配问题责任人。
     */
    @PostMapping(
            "/tasks/{taskId}"
            + "/issues/{issueId}/assign"
    )
    public Result<DocumentQualityVO.Detail>
            assignIssue(

            @PathVariable Long courseId,

            @PathVariable Long taskId,

            @PathVariable Long issueId,

            @RequestBody
            DocumentQualityRequest.AssignIssue request,

            Authentication auth) {

        return Result.success(

                service.assignIssue(

                        courseId,

                        taskId,

                        issueId,

                        userId(auth),

                        request.assigneeId()
                )
        );
    }

    /**
     * 提交整改结果。
     */
    @PostMapping(
            "/tasks/{taskId}"
            + "/issues/{issueId}/resolve"
    )
    public Result<DocumentQualityVO.Detail>
            resolveIssue(

            @PathVariable Long courseId,

            @PathVariable Long taskId,

            @PathVariable Long issueId,

            @RequestBody
            DocumentQualityRequest.ResolveIssue request,

            Authentication auth) {

        return Result.success(

                service.resolveIssue(

                        courseId,

                        taskId,

                        issueId,

                        userId(auth),

                        request
                )
        );
    }

    /**
     * 复核整改结果。
     */
    @PostMapping(
            "/tasks/{taskId}"
            + "/issues/{issueId}/review"
    )
    public Result<DocumentQualityVO.Detail>
            reviewIssue(

            @PathVariable Long courseId,

            @PathVariable Long taskId,

            @PathVariable Long issueId,

            @RequestBody
            DocumentQualityRequest.ReviewIssue request,

            Authentication auth) {

        return Result.success(

                service.reviewIssue(

                        courseId,

                        taskId,

                        issueId,

                        userId(auth),

                        request
                )
        );
    }

    /**
     * 完成抽检任务。
     */
    @PostMapping("/tasks/{taskId}/complete")
    public Result<DocumentQualityVO.Detail>
            completeTask(

            @PathVariable Long courseId,

            @PathVariable Long taskId,

            Authentication auth) {

        return Result.success(

                service.completeTask(

                        courseId,

                        taskId,

                        userId(auth)
                )
        );
    }

    /**
     * 查询文档质量控制状态。
     */
    @GetMapping("/documents/{documentId}/control")
    public Result<DocumentQualityVO.Control>
            getDocumentControl(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            Authentication auth) {

        return Result.success(
                service.getDocumentControl(
                        courseId,
                        documentId,
                        userId(auth)
                )
        );
    }

    /**
     * 暂停资料检索。
     */
    @PostMapping("/documents/{documentId}/suspend")
    public Result<DocumentQualityVO.Control>
            suspendDocument(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody
            DocumentQualityRequest.ControlAction request,

            Authentication auth) {

        return Result.success(
                service.suspendDocument(
                        courseId,
                        documentId,
                        userId(auth),
                        request
                )
        );
    }

    /**
     * 恢复资料检索。
     */
    @PostMapping("/documents/{documentId}/resume")
    public Result<DocumentQualityVO.Control>
            resumeDocument(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            @RequestBody
            DocumentQualityRequest.ControlAction request,

            Authentication auth) {

        return Result.success(
                service.resumeDocument(
                        courseId,
                        documentId,
                        userId(auth),
                        request
                )
        );
    }

    /**
     * 对指定课程资料执行自动质量检查。
     *
     * 检查结果直接进入现有质量整改流程。
     */
    @PostMapping("/documents/{documentId}/scan")
    public Result<DocumentQualityVO.Detail> scanDocument(

            @PathVariable Long courseId,

            @PathVariable Long documentId,

            Authentication auth) {

        return Result.success(
                scanService.scan(
                        courseId,
                        documentId,
                        userId(auth)
                )
        );
    }
}
