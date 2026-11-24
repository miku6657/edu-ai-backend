package com.edu.ai.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.document.entity.CourseDocument;
import com.edu.ai.document.entity.CourseEmbeddingProfile;
import com.edu.ai.document.mapper.CourseDocumentMapper;
import com.edu.ai.document.mapper.CourseEmbeddingProfileMapper;
import com.edu.ai.system.entity.UserConfig;
import com.edu.ai.system.mapper.UserConfigMapper;
import com.edu.ai.system.security.ConfigCryptoService;
import com.edu.ai.system.service.PlatformModelOwnerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CourseEmbeddingProfileService {

    private final CourseEmbeddingProfileMapper profileMapper;
    private final CourseDocumentMapper documentMapper;
    private final UserConfigMapper configMapper;
    private final ConfigCryptoService crypto;
    private final PlatformModelOwnerService platformModelOwnerService;

    public CourseEmbeddingProfileService(
            CourseEmbeddingProfileMapper profileMapper,
            CourseDocumentMapper documentMapper,
            UserConfigMapper configMapper,
            ConfigCryptoService crypto,
            PlatformModelOwnerService platformModelOwnerService) {

        this.profileMapper = profileMapper;
        this.documentMapper = documentMapper;
        this.configMapper = configMapper;
        this.crypto = crypto;
        this.platformModelOwnerService = platformModelOwnerService;
    }

    public CourseEmbeddingProfile require(
            Long courseId,
            int version) {

        CourseEmbeddingProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<CourseEmbeddingProfile>()
                        .eq(
                                CourseEmbeddingProfile::getCourseId,
                                courseId
                        )
                        .eq(
                                CourseEmbeddingProfile::getEmbeddingVersion,
                                version
                        )
        );

        if (profile == null) {
            throw new BusinessException(
                    409,
                    "课程索引缺少对应的模型配置，"
                    + "请先确认历史索引来源或重新构建"
            );
        }

        return profile;
    }

    public String decryptKey(CourseEmbeddingProfile profile) {

        String ciphertext = profile.getApiKeyCiphertext();

        if (ciphertext == null || ciphertext.isBlank()) {
            return "";
        }

        String key = crypto.decrypt(ciphertext);

        if (!crypto.matches(
                profile.getApiKeyFingerprint(),
                key)) {

            throw new IllegalStateException(
                    "课程索引模型密钥指纹不匹配"
            );
        }

        return key;
    }

    /**
     * 首次索引前初始化版本1的模型配置。
     *
     * 必须在已经取得课程索引锁的情况下调用。
     * 已存在READY文档的历史课程不会自动绑定当前模型。
     */
    @Transactional(rollbackFor = Exception.class)
    public CourseEmbeddingProfile ensureInitial(
            Long userId,
            Long courseId) {

        CourseEmbeddingProfile existing = profileMapper.selectOne(
                new LambdaQueryWrapper<CourseEmbeddingProfile>()
                        .eq(
                                CourseEmbeddingProfile::getCourseId,
                                courseId
                        )
                        .eq(
                                CourseEmbeddingProfile::getEmbeddingVersion,
                                1
                        )
        );

        if (existing != null) {
            return existing;
        }

        long readyCount = documentMapper.selectCount(
                new LambdaQueryWrapper<CourseDocument>()
                        .eq(CourseDocument::getCourseId, courseId)
                        .eq(CourseDocument::getStatus, "READY")
        );

        if (readyCount != 0) {
            throw new BusinessException(
                    409,
                    "历史课程已有向量，不能自动推断原Embedding模型"
            );
        }

        UserConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>()
                        .eq(
                                UserConfig::getUserId,
                                platformModelOwnerService
                                        .requirePlatformUserId()
                        )
        );

        if (config == null
                || blank(config.getEmbeddingProvider())
                || blank(config.getEmbeddingBaseUrl())
                || blank(config.getEmbeddingModel())
                || config.getEmbeddingDim() == null
                || config.getEmbeddingDim() <= 0) {

            throw new BusinessException(
                    400,
                    "请先完成Embedding模型配置"
            );
        }

        CourseEmbeddingProfile profile =
                new CourseEmbeddingProfile();

        profile.setCourseId(courseId);
        profile.setEmbeddingVersion(1);

        profile.setProvider(config.getEmbeddingProvider());
        profile.setBaseUrl(config.getEmbeddingBaseUrl());
        profile.setModel(config.getEmbeddingModel());
        profile.setDimension(config.getEmbeddingDim());

        profile.setApiKeyCiphertext(
                config.getEmbeddingApiKeyCiphertext()
        );

        profile.setApiKeyFingerprint(
                config.getEmbeddingApiKeyFingerprint()
        );

        profile.setCollectionName("course_" + courseId);

        profile.setStatus("ACTIVE");

        // 先验证密钥快照可以解密。
        decryptKey(profile);

        profile.setCreateTime(LocalDateTime.now());

        profileMapper.insert(profile);

        return profile;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
