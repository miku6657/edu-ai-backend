package com.edu.ai.system.controller;

import com.edu.ai.common.BusinessException;
import com.edu.ai.common.Result;
import com.edu.ai.system.dto.PreferencesUpdateRequest;
import com.edu.ai.system.dto.UserConfigVO;
import com.edu.ai.system.service.PlatformModelOwnerService;
import com.edu.ai.system.service.UserConfigService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
public class UserConfigController {

    private final UserConfigService userConfigService;
    private final PlatformModelOwnerService platformOwner;

    public UserConfigController(
            UserConfigService userConfigService,
            PlatformModelOwnerService platformOwner) {

        this.userConfigService = userConfigService;
        this.platformOwner = platformOwner;
    }

    /**
     * GET /api/v1/user/config
     */
    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserConfigVO> getConfig(
            Authentication authentication) {

        getUserId(authentication);

        return Result.success(
                userConfigService.getConfig(
                        platformOwner.requirePlatformUserId()
                )
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
