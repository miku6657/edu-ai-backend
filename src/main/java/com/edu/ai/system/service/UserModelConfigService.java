package com.edu.ai.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.common.BusinessException;

import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;

import com.edu.ai.system.dto.ModelConfigUpdateRequest;
import com.edu.ai.system.dto.ModelProbeRequest;
import com.edu.ai.system.dto.ModelProbeResultVO;
import com.edu.ai.system.dto.UserConfigVO;

import com.edu.ai.system.entity.User;
import com.edu.ai.system.entity.UserConfig;

import com.edu.ai.system.mapper.UserConfigMapper;
import com.edu.ai.system.mapper.UserMapper;

import com.edu.ai.system.security.ConfigCryptoService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class UserModelConfigService {

    private final UserMapper userMapper;
    private final UserConfigMapper configMapper;
    private final CourseMapper courseMapper;
    private final ConfigCryptoService cryptoService;
    private final AiModelGateway aiModelGateway;
    private final UserConfigService userConfigService;
    private final ModelConfigFingerprint fingerprint;
    private final ModelConfigValidator configValidator;

    public UserModelConfigService(
            UserMapper userMapper,
            UserConfigMapper configMapper,
            CourseMapper courseMapper,
            ConfigCryptoService cryptoService,
            AiModelGateway aiModelGateway,
            UserConfigService userConfigService,
            ModelConfigFingerprint fingerprint,
            ModelConfigValidator configValidator) {

        this.userMapper = userMapper;
        this.configMapper = configMapper;
        this.courseMapper = courseMapper;
        this.cryptoService = cryptoService;
        this.aiModelGateway = aiModelGateway;
        this.userConfigService = userConfigService;
        this.fingerprint = fingerprint;
        this.configValidator = configValidator;
    }

    /**
     * 单独探测一个模型。
     * 不修改数据库。
     */
    public ModelProbeResultVO probe(
            Long userId,
            ModelProbeRequest request) {

        requireUser(userId);

        if (request == null || request.getProbeType() == null) {
            throw new BusinessException(400, "探测参数不完整");
        }

        String type = request.getProbeType().name();

        UserConfig config = findConfig(userId);

        String apiKey = resolveProbeKey(config, request);

        configValidator.validate(
                type,
                request.getProvider(),
                request.getBaseUrl(),
                request.getModelName(),
                apiKey
        );

        ModelProbeResultVO result = aiModelGateway.probe(
                request,
                apiKey
        );

        if (!result.isReachable()) {
            throw new BusinessException(
                    400,
                    "模型探测失败，请检查供应商、Base URL、API Key 和模型名称"
            );
        }

        if (request.getProbeType()
                == ModelProbeRequest.ProbeType.EMBEDDING
                && (result.getActualDim() == null
                || result.getActualDim() <= 0)) {

            throw new BusinessException(
                    400,
                    "Embedding 模型没有返回有效向量维度"
            );
        }

        return result;
    }

    /**
     * 保存模型配置。
     *
     * 保存之前重新探测两个模型。
     * 只有两个模型均通过，才写入数据库。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserConfigVO save(
            Long userId,
            ModelConfigUpdateRequest request) {

        if (request == null) {
            throw new BusinessException(400, "配置参数不能为空");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getId, userId)
                        .eq(User::getStatus, 1)
                        .last("LIMIT 1 FOR UPDATE")
        );

        if (user == null) {
            throw new BusinessException(404, "用户不存在或已禁用");
        }

        UserConfig old = findConfig(userId);

        String chatKey = resolveNewOrSavedKey(
                request.getChatApiKey(),
                old == null ? null : old.getChatApiKeyCiphertext(),
                request.getChatProvider()
        );

        String embeddingKey;

        if (request.isReuseChatApiKey()) {
            embeddingKey = chatKey;
        } else {
            embeddingKey = resolveNewOrSavedKey(
                    request.getEmbeddingApiKey(),
                    old == null ? null : old.getEmbeddingApiKeyCiphertext(),
                    request.getDefaultEmbeddingProvider()
            );
        }

        configValidator.validate(
                "CHAT",
                request.getChatProvider(),
                request.getChatBaseUrl(),
                request.getChatModel(),
                chatKey
        );

        configValidator.validate(
                "EMBEDDING",
                request.getDefaultEmbeddingProvider(),
                request.getEmbeddingBaseUrl(),
                request.getDefaultEmbeddingModel(),
                embeddingKey
        );

        /*
         * 先探测对话模型。
         */
        ModelProbeRequest chatProbeRequest =
                new ModelProbeRequest();

        chatProbeRequest.setProbeType(
                ModelProbeRequest.ProbeType.CHAT
        );
        chatProbeRequest.setProvider(request.getChatProvider());
        chatProbeRequest.setBaseUrl(request.getChatBaseUrl());
        chatProbeRequest.setModelName(request.getChatModel());

        ModelProbeResultVO chatResult = aiModelGateway.probe(
                chatProbeRequest,
                chatKey
        );

        if (!chatResult.isReachable()) {
            throw new BusinessException(
                    400,
                    "对话模型探测失败，配置未保存"
            );
        }

        /*
         * 再探测 Embedding。
         */
        ModelProbeRequest embeddingProbeRequest =
                new ModelProbeRequest();

        embeddingProbeRequest.setProbeType(
                ModelProbeRequest.ProbeType.EMBEDDING
        );
        embeddingProbeRequest.setProvider(
                request.getDefaultEmbeddingProvider()
        );
        embeddingProbeRequest.setBaseUrl(
                request.getEmbeddingBaseUrl()
        );
        embeddingProbeRequest.setModelName(
                request.getDefaultEmbeddingModel()
        );

        ModelProbeResultVO embeddingResult = aiModelGateway.probe(
                embeddingProbeRequest,
                embeddingKey
        );

        if (!embeddingResult.isReachable()
                || embeddingResult.getActualDim() == null
                || embeddingResult.getActualDim() <= 0) {

            throw new BusinessException(
                    400,
                    "Embedding 模型探测失败，配置未保存"
            );
        }

        /*
         * 检查 Embedding 目标是否变化。
         */
        String chatFingerprint =
                cryptoService.fingerprint(chatKey);

        boolean targetChanged = fingerprint.embeddingChanged(
                old,
                request.getDefaultEmbeddingProvider(),
                request.getEmbeddingBaseUrl(),
                request.getDefaultEmbeddingModel(),
                embeddingKey,
                request.isReuseChatApiKey(),
                chatFingerprint
        );

        long courseCount = courseMapper.selectCount(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getUserId, userId)
        );

        if (targetChanged && courseCount > 0) {
            if (!request.isMigrateExistingCourses()) {
                throw new BusinessException(
                        409,
                        "修改 Embedding 配置会影响已有课程，请确认迁移"
                );
            }

            /*
             * 目前还没有完成课程向量迁移任务。
             * 必须阻止保存，不能只修改配置而保留旧向量。
             */
            throw new BusinessException(
                    501,
                    "课程 Embedding 迁移功能尚未实现，暂不能切换模型"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        UserConfig config = old == null
                ? new UserConfig()
                : old;

        config.setUserId(userId);

        config.setChatProvider(request.getChatProvider());
        config.setChatBaseUrl(request.getChatBaseUrl().trim());
        config.setChatModel(request.getChatModel().trim());

        config.setChatApiKeyCiphertext(
                cryptoService.encrypt(chatKey)
        );
        config.setChatApiKeyFingerprint(
                cryptoService.fingerprint(chatKey)
        );

        config.setEmbeddingProvider(
                request.getDefaultEmbeddingProvider()
        );

        config.setEmbeddingBaseUrl(
                request.getEmbeddingBaseUrl().trim()
        );

        config.setEmbeddingModel(
                request.getDefaultEmbeddingModel().trim()
        );

        config.setEmbeddingApiKeyCiphertext(
                cryptoService.encrypt(embeddingKey)
        );
        config.setEmbeddingApiKeyFingerprint(
                cryptoService.fingerprint(embeddingKey)
        );

        config.setEmbeddingDim(embeddingResult.getActualDim());

        if (config.getCreateTime() == null) {
            config.setCreateTime(now);
        }

        config.setUpdateTime(now);

        if (old == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }

        return userConfigService.getConfig(userId);
    }

    /**
     * 清除指定 API Key。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserConfigVO clearKeys(
            Long userId,
            String target) {

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getId, userId)
                        .eq(User::getStatus, 1)
                        .last("LIMIT 1 FOR UPDATE")
        );

        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (!Set.of("ALL", "CHAT", "EMBEDDING").contains(target)) {
            throw new BusinessException(400, "不支持的密钥清理类型");
        }

        UserConfig config = findConfig(userId);

        if (config == null) {
            return userConfigService.getConfig(userId);
        }

        if ("ALL".equals(target) || "CHAT".equals(target)) {
            config.setChatApiKeyCiphertext(null);
            config.setChatApiKeyFingerprint(null);
        }

        if ("ALL".equals(target) || "EMBEDDING".equals(target)) {
            config.setEmbeddingApiKeyCiphertext(null);
            config.setEmbeddingApiKeyFingerprint(null);
        }

        config.setUpdateTime(LocalDateTime.now());

        configMapper.updateById(config);

        return userConfigService.getConfig(userId);
    }

    private String resolveProbeKey(
            UserConfig config,
            ModelProbeRequest request) {

        String typedKey = normalize(request.getApiKey());

        if (typedKey != null && request.isUseSavedKey()) {
            throw new BusinessException(
                    400,
                    "不能同时提供新 API Key 和使用已保存 Key"
            );
        }

        if (typedKey != null) {
            return typedKey;
        }

        if (request.isUseSavedKey()) {
            if (config == null) {
                throw new BusinessException(
                        400,
                        "当前用户没有已保存的 API Key"
                );
            }

            String ciphertext =
                    request.getProbeType()
                            == ModelProbeRequest.ProbeType.CHAT
                    ? config.getChatApiKeyCiphertext()
                    : config.getEmbeddingApiKeyCiphertext();

            if (ciphertext == null || ciphertext.isBlank()) {
                throw new BusinessException(
                        400,
                        "当前模型没有已保存的 API Key"
                );
            }

            return cryptoService.decrypt(ciphertext);
        }

        if ("OLLAMA".equals(request.getProvider())) {
            return "";
        }

        throw new BusinessException(
                400,
                "请填写 API Key 或选择使用已保存的 Key"
        );
    }

    private String resolveNewOrSavedKey(
            String newKey,
            String oldCiphertext,
            String provider) {

        String normalized = normalize(newKey);

        if (normalized != null) {
            return normalized;
        }

        if (oldCiphertext != null && !oldCiphertext.isBlank()) {
            return cryptoService.decrypt(oldCiphertext);
        }

        if ("OLLAMA".equals(provider)) {
            return "";
        }

        throw new BusinessException(
                400,
                "请提供 API Key"
        );
    }

    private UserConfig findConfig(Long userId) {
        return configMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>()
                        .eq(UserConfig::getUserId, userId)
        );
    }

    private void requireUser(Long userId) {

        if (userId == null || userId <= 0
                || userMapper.selectById(userId) == null) {

            throw new BusinessException(
                    401, "用户登录状态无效"
            );
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
