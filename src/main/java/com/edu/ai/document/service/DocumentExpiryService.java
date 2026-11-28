package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.service.CoursePermissionService;
import com.edu.ai.document.dto.DocumentExpiryRequest;
import com.edu.ai.document.dto.DocumentExpiryVO;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.mapper.CourseDocumentMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class DocumentExpiryService {

    private static final ZoneId APP_ZONE =
            ZoneId.of("Asia/Shanghai");

    private static final int DEFAULT_REMIND_DAYS = 7;

    private static final int MAX_REMIND_DAYS = 90;

    private final CourseDocumentMapper documentMapper;

    private final CoursePermissionService permissions;

    public DocumentExpiryService(
            CourseDocumentMapper documentMapper,
            CoursePermissionService permissions) {

        this.documentMapper = documentMapper;
        this.permissions = permissions;
    }

    /**
     * 查询资料有效期配置。
     */
    public DocumentExpiryVO getExpiry(
            Long courseId,
            Long documentId,
            Long userId) {

        Course course = permissions.requireManage(
                courseId,
                userId
        );

        CourseDocument document =
                requireDocument(courseId, documentId);

        return toVO(document, course);
    }

    /**
     * 修改资料有效期和更新责任人。
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentExpiryVO updateExpiry(
            Long courseId,
            Long documentId,
            Long userId,
            DocumentExpiryRequest request) {

        Course course = permissions.requireManage(
                courseId,
                userId
        );

        CourseDocument document =
                requireDocument(courseId, documentId);

        if ("HISTORY".equals(document.getVersionStatus())) {

            throw new BusinessException(
                    409,
                    "历史版本不能修改有效期配置"
            );
        }

        if (!"READY".equals(document.getStatus())) {

            throw new BusinessException(
                    409,
                    "只有已完成索引的资料才能配置有效期"
            );
        }

        validateRequest(request);

        Long responsibleId =
                request.updateResponsibleId();

        // 不指定责任人时，默认由课程负责人承担。
        if (responsibleId != null) {

            permissions.requireManage(
                    courseId,
                    responsibleId
            );
        }

        int updated = documentMapper.update(
                null,
                new LambdaUpdateWrapper<CourseDocument>()

                        .eq(
                                CourseDocument::getId,
                                documentId
                        )

                        .eq(
                                CourseDocument::getCourseId,
                                courseId
                        )

                        .eq(
                                CourseDocument::getStatus,
                                "READY"
                        )

                        .eq(
                                CourseDocument::getVersionStatus,
                                document.getVersionStatus()
                        )

                        .ne(
                                CourseDocument::getVersionStatus,
                                "HISTORY"
                        )

                        .set(
                                CourseDocument::getValidUntil,
                                request.validUntil()
                        )

                        .set(
                                CourseDocument::getUpdateResponsibleId,
                                responsibleId
                        )

                        .set(
                                CourseDocument::getExpiryRemindDays,
                                request.expiryRemindDays()
                        )

                        .set(
                                CourseDocument::getUpdateTime,
                                LocalDateTime.now(APP_ZONE)
                        )
        );

        if (updated != 1) {

            throw new BusinessException(
                    409,
                    "资料状态已发生变化，请刷新后重试"
            );
        }

        CourseDocument latest =
                requireDocument(courseId, documentId);

        return toVO(latest, course);
    }

    /**
     * 校验有效期请求。
     */
    private void validateRequest(
            DocumentExpiryRequest request) {

        if (request == null) {

            throw new BusinessException(
                    400,
                    "有效期配置不能为空"
            );
        }

        Integer remindDays =
                request.expiryRemindDays();

        if (remindDays == null
                || remindDays < 0
                || remindDays > MAX_REMIND_DAYS) {

            throw new BusinessException(
                    400,
                    "提前提醒天数必须在0至90天之间"
            );
        }
    }

    /**
     * 查询指定课程的有效文档记录。
     */
    private CourseDocument requireDocument(
            Long courseId,
            Long documentId) {

        CourseDocument document =
                documentMapper.selectById(documentId);

        if (document == null
                || !courseId.equals(document.getCourseId())
                || "DELETED".equals(document.getStatus())
                || "DELETING".equals(document.getStatus())) {

            throw new BusinessException(
                    404,
                    "课程资料不存在"
            );
        }

        return document;
    }

    /**
     * 计算剩余有效天数。
     */
    private Long remainingDays(LocalDate validUntil) {

        if (validUntil == null) {
            return null;
        }

        return ChronoUnit.DAYS.between(
                LocalDate.now(APP_ZONE),
                validUntil
        );
    }

    /**
     * 实时计算资料有效期状态。
     */
    private String expiryStatus(
            Long remainingDays,
            int remindDays) {

        if (remainingDays == null) {
            return "UNLIMITED";
        }

        if (remainingDays < 0) {
            return "EXPIRED";
        }

        if (remainingDays <= remindDays) {
            return "EXPIRING";
        }

        return "VALID";
    }

    private DocumentExpiryVO toVO(
            CourseDocument document,
            Course course) {

        int remindDays =
                document.getExpiryRemindDays() == null
                        ? DEFAULT_REMIND_DAYS
                        : document.getExpiryRemindDays();

        Long responsibleId =
                document.getUpdateResponsibleId() == null
                        ? course.getUserId()
                        : document.getUpdateResponsibleId();

        Long remaining =
                remainingDays(document.getValidUntil());

        return new DocumentExpiryVO(
                String.valueOf(document.getId()),

                String.valueOf(document.getCourseId()),

                document.getOriginalName(),

                document.getValidUntil(),

                responsibleId == null
                        ? null
                        : responsibleId.toString(),

                remindDays,

                expiryStatus(remaining, remindDays),

                remaining
        );
    }
}
