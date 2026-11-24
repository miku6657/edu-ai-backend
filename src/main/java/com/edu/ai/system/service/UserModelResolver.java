package com.edu.ai.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.document.entity.CourseEmbeddingProfile;
import com.edu.ai.document.service.CourseEmbeddingProfileService;
import com.edu.ai.system.entity.UserConfig;
import com.edu.ai.system.mapper.UserConfigMapper;
import com.edu.ai.system.security.ConfigCryptoService;

import org.springframework.stereotype.Service;

@Service
public class UserModelResolver {

    private final UserConfigMapper configMapper;
    private final ConfigCryptoService cryptoService;
    private final CourseEmbeddingProfileService profileService;
    private final PlatformModelOwnerService platformModelOwnerService;

    public UserModelResolver(
            UserConfigMapper configMapper,
            ConfigCryptoService cryptoService,
            CourseEmbeddingProfileService profileService,
            PlatformModelOwnerService platformModelOwnerService) {

        this.configMapper = configMapper;
        this.cryptoService = cryptoService;
        this.profileService = profileService;
        this.platformModelOwnerService = platformModelOwnerService;
    }

    public UserModelConfig resolve(Long userId) {

        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不合法");
        }

        Long platformUserId =
                platformModelOwnerService.requirePlatformUserId();

        UserConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>()
                        .eq(UserConfig::getUserId, platformUserId)
                        .last("LIMIT 1")
        );

        if (config == null) {
            throw new BusinessException(
                    400,
                    "请先配置个人模型"
            );
        }

        String chatKey = decrypt(
                config.getChatApiKeyCiphertext()
        );

        String embeddingKey = decrypt(
                config.getEmbeddingApiKeyCiphertext()
        );

        validate(
                config.getChatProvider(),
                config.getChatBaseUrl(),
                config.getChatModel()
        );

        validate(
                config.getEmbeddingProvider(),
                config.getEmbeddingBaseUrl(),
                config.getEmbeddingModel()
        );

        if (config.getEmbeddingDim() == null
                || config.getEmbeddingDim() <= 0) {

            throw new BusinessException(
                    400,
                    "Embedding维度配置不合法"
            );
        }

        return new UserModelConfig(
                config.getChatProvider(),
                config.getChatBaseUrl(),
                chatKey,
                config.getChatModel(),

                config.getEmbeddingProvider(),
                config.getEmbeddingBaseUrl(),
                embeddingKey,
                config.getEmbeddingModel(),
                config.getEmbeddingDim(),
                null
        );
    }

    /**
     * 对话模型使用用户当前配置；
     * Embedding模型使用课程对应索引版本的固定配置。
     */
    public UserModelConfig resolveForCourse(
            Long userId,
            Long courseId,
            int embeddingVersion) {

        UserModelConfig current = resolveChatOnly(userId);

        CourseEmbeddingProfile profile =
                profileService.require(
                        courseId,
                        embeddingVersion
                );

        if (!"ACTIVE".equals(profile.getStatus())) {
            throw new BusinessException(
                    409,
                    "课程Embedding索引版本尚未激活"
            );
        }

        if (profile.getCollectionName() == null
                || profile.getCollectionName().isBlank()) {

            throw new BusinessException(
                    409,
                    "课程索引Collection配置缺失"
            );
        }

        if (profile.getDimension() == null
                || profile.getDimension() <= 0) {

            throw new BusinessException(
                    409,
                    "课程Embedding维度不合法"
            );
        }

        validate(
                profile.getProvider(),
                profile.getBaseUrl(),
                profile.getModel()
        );

        String embeddingApiKey =
                profileService.decryptKey(profile);

        return new UserModelConfig(
                current.chatProvider(),
                current.chatBaseUrl(),
                current.chatApiKey(),
                current.chatModel(),

                profile.getProvider(),
                profile.getBaseUrl(),
                embeddingApiKey,
                profile.getModel(),
                profile.getDimension(),
                profile.getCollectionName()
        );
    }

    /**
     * 只解析用户当前 Chat 模型。
     * 不依赖用户当前 Embedding 配置。
     */
    private UserModelConfig resolveChatOnly(Long userId) {

        if (userId == null || userId <= 0) {
            throw new BusinessException(
                    400,
                    "用户ID不合法"
            );
        }

        UserConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>()
                        .eq(
                                UserConfig::getUserId,
                                platformModelOwnerService
                                        .requirePlatformUserId()
                        )
                        .last("LIMIT 1")
        );

        if (config == null) {
            throw new BusinessException(
                    400,
                    "平台尚未配置Chat模型，请联系管理员"
            );
        }

        String chatKey = decrypt(
                config.getChatApiKeyCiphertext()
        );

        validate(
                config.getChatProvider(),
                config.getChatBaseUrl(),
                config.getChatModel()
        );

        return new UserModelConfig(
                config.getChatProvider(),
                config.getChatBaseUrl(),
                chatKey,
                config.getChatModel(),

                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private String decrypt(String ciphertext) {

        if (ciphertext == null || ciphertext.isBlank()) {
            return "";
        }

        return cryptoService.decrypt(ciphertext);
    }

    private void validate(
            String provider,
            String baseUrl,
            String model) {

        if (provider == null || provider.isBlank()
                || baseUrl == null || baseUrl.isBlank()
                || model == null || model.isBlank()) {

            throw new BusinessException(
                    400,
                    "个人模型配置不完整"
            );
        }
    }

    /**
     * 仅在服务端内部传递，绝不作为 Controller 响应返回给前端。
     */
    public record UserModelConfig(
            String chatProvider,
            String chatBaseUrl,
            String chatApiKey,
            String chatModel,

            String embeddingProvider,
            String embeddingBaseUrl,
            String embeddingApiKey,
            String embeddingModel,
            Integer embeddingDimension,

            String collectionName
    ) {
    }
}
