package com.edu.ai.task.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.task.entity.DdlExtractRequest;
import com.edu.ai.task.mapper.DdlExtractRequestMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DdlExtractIdempotencyService {

    private final DdlExtractRequestMapper requestMapper;
    private final DdlRequestHashService hashService;

    public DdlExtractIdempotencyService(
            DdlExtractRequestMapper requestMapper,
            DdlRequestHashService hashService) {

        this.requestMapper = requestMapper;
        this.hashService = hashService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DdlExtractRequest acquire(
            Long userId,
            String clientRequestId,
            String requestHash) {

        DdlExtractRequest existing =
                requestMapper.findByRequestIdForUpdate(
                        userId,
                        clientRequestId
                );

        if (existing != null) {
            return validateOrReclaim(existing, requestHash);
        }

        String token = UUID.randomUUID().toString();

        DdlExtractRequest request =
                new DdlExtractRequest();

        request.setUserId(userId);
        request.setClientRequestId(clientRequestId);
        request.setRequestHash(requestHash);
        request.setProcessingToken(token);
        request.setLeaseUntil(
                LocalDateTime.now().plusMinutes(15)
        );
        request.setStatus("PROCESSING");
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());

        try {
            requestMapper.insert(request);
            return request;

        } catch (DuplicateKeyException e) {

            DdlExtractRequest duplicated =
                    requestMapper.findByRequestIdForUpdate(
                            userId,
                            clientRequestId
                    );

            if (duplicated == null) {
                throw new BusinessException(
                        409,
                        "请求正在竞争，请稍后重试"
                );
            }

            return validateOrReclaim(
                    duplicated,
                    requestHash
            );
        }
    }

    private DdlExtractRequest validateOrReclaim(
            DdlExtractRequest existing,
            String requestHash) {

        if (!requestHash.equals(existing.getRequestHash())) {
            throw new BusinessException(
                    409,
                    "请求编号已被其他内容使用，请更换请求编号"
            );
        }

        if ("SUCCEEDED".equals(existing.getStatus())) {
            return existing;
        }

        if ("PROCESSING".equals(existing.getStatus())) {

            LocalDateTime now = LocalDateTime.now();

            if (existing.getLeaseUntil() == null
                    || existing.getLeaseUntil().isAfter(now)) {

                throw new BusinessException(
                        409,
                        "该请求正在处理中，请稍后重试"
                );
            }

            String newToken = UUID.randomUUID().toString();

            int updated = requestMapper.reclaimExpired(
                    existing.getId(),
                    newToken
            );

            if (updated == 1) {
                existing.setProcessingToken(newToken);
                existing.setLeaseUntil(now.plusMinutes(15));
                existing.setUpdatedAt(now);
                return existing;
            }

            throw new BusinessException(
                    409,
                    "请求已被其他线程接管，请稍后重试"
            );
        }

        if ("FAILED".equals(existing.getStatus())) {
            throw new BusinessException(
                    409,
                    "上次提取失败，请使用新的请求编号重试"
            );
        }

        throw new BusinessException(
                500,
                "请求记录状态异常"
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(
            Long requestId,
            String token,
            Long taskId,
            String outcome,
            String responseJson) {

        int updated = requestMapper.markSucceeded(
                requestId,
                token,
                taskId,
                outcome,
                responseJson
        );

        if (updated != 1) {
            throw new BusinessException(
                    409,
                    "请求处理权已失效，不能提交结果"
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
            Long requestId,
            String token,
            String errorMessage) {

        String safeMessage = errorMessage;

        if (safeMessage != null && safeMessage.length() > 1000) {
            safeMessage = safeMessage.substring(0, 1000);
        }

        requestMapper.markFailed(
                requestId,
                token,
                safeMessage
        );
    }
}
