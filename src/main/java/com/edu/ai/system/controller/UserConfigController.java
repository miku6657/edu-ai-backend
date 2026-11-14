package com.edu.ai.system.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.system.dto.PreferencesUpdateRequest;
import com.edu.ai.system.dto.UserConfigVO;
import com.edu.ai.system.service.UserConfigService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
public class UserConfigController {

    private final UserConfigService userConfigService;

    public UserConfigController(
            UserConfigService userConfigService) {

        this.userConfigService = userConfigService;
    }

    /**
     * GET /api/v1/user/config
     */
    @GetMapping("/config")
    public Result<UserConfigVO> getConfig(
            Authentication authentication) {

        Long userId = getUserId(authentication);

        return Result.success(
                userConfigService.getConfig(userId)
        );
    }

    /**
     * PUT /api/v1/user/preferences
     */
    @PutMapping("/preferences")
    public Result<UserConfigVO> updatePreferences(
            @Valid @RequestBody
            PreferencesUpdateRequest request,
            Authentication authentication) {

        Long userId = getUserId(authentication);

        return Result.success(
                userConfigService.updatePreferences(
                        userId, request)
        );
    }

    private Long getUserId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof Long userId)) {

            throw new BusinessException(
                    401, "登录状态无效");
        }

        return userId;
    }
}
