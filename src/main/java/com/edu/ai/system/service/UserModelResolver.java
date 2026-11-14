package com.edu.ai.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.system.entity.UserConfig;
import com.edu.ai.system.mapper.UserConfigMapper;
import com.edu.ai.system.security.ConfigCryptoService;

import org.springframework.stereotype.Service;

@Service
public class UserModelResolver {

    private final UserConfigMapper configMapper;
    private final ConfigCryptoService cryptoService;

    public UserModelResolver(
            UserConfigMapper configMapper,
            ConfigCryptoService cryptoService) {

        this.configMapper = configMapper;
        this.cryptoService = cryptoService;
    }

    public UserModelConfig resolve(Long userId) {

        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不合法");
        }

        UserConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>()
                        .eq(UserConfig::getUserId, userId)
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
                config.getEmbeddingDim()
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
            Integer embeddingDimension
    ) {
    }
}
