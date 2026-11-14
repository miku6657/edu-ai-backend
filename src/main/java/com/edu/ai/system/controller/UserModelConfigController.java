package com.edu.ai.system.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;

import com.edu.ai.system.dto.ModelConfigUpdateRequest;
import com.edu.ai.system.dto.ModelProbeRequest;
import com.edu.ai.system.dto.ModelProbeResultVO;
import com.edu.ai.system.dto.UserConfigVO;

import com.edu.ai.system.service.UserModelConfigService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/model-config")
public class UserModelConfigController {

    private final UserModelConfigService service;

    public UserModelConfigController(
            UserModelConfigService service) {
        this.service = service;
    }

    /**
     * POST /api/v1/user/model-config/probe
     */
    @PostMapping("/probe")
    public Result<ModelProbeResultVO> probe(
            @Valid @RequestBody ModelProbeRequest request,
            Authentication authentication) {

        return Result.success(
                service.probe(
                        getUserId(authentication),
                        request
                )
        );
    }

    /**
     * PUT /api/v1/user/model-config
     */
    @PutMapping
    public Result<UserConfigVO> save(
            @Valid @RequestBody ModelConfigUpdateRequest request,
            Authentication authentication) {

        return Result.success(
                service.save(
                        getUserId(authentication),
                        request
                )
        );
    }

    /**
     * DELETE /api/v1/user/model-config/keys?target=ALL
     */
    @DeleteMapping("/keys")
    public Result<UserConfigVO> clearKeys(
            @RequestParam(defaultValue = "ALL") String target,
            Authentication authentication) {

        return Result.success(
                service.clearKeys(
                        getUserId(authentication),
                        target
                )
        );
    }

    private Long getUserId(Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof Long userId)) {

            throw new BusinessException(
                    401, "登录状态无效");
        }

        return userId;
    }
}
