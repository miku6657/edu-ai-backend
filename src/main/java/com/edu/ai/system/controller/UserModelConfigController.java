package com.edu.ai.system.controller;

import com.edu.ai.common.Result;
import com.edu.ai.system.dto.ModelConfigUpdateRequest;
import com.edu.ai.system.dto.ModelProbeRequest;
import com.edu.ai.system.dto.ModelProbeResultVO;
import com.edu.ai.system.dto.UserConfigVO;
import com.edu.ai.system.service.PlatformModelOwnerService;
import com.edu.ai.system.service.UserModelConfigService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/model-config")
@PreAuthorize("hasRole('ADMIN')")
public class UserModelConfigController {

    private final UserModelConfigService service;
    private final PlatformModelOwnerService platformOwner;

    public UserModelConfigController(
            UserModelConfigService service,
            PlatformModelOwnerService platformOwner) {

        this.service = service;
        this.platformOwner = platformOwner;
    }

    @PostMapping("/probe")
    public Result<ModelProbeResultVO> probe(
            @Valid @RequestBody ModelProbeRequest request) {

        return Result.success(
                service.probe(
                        platformOwner.requirePlatformUserId(),
                        request
                )
        );
    }

    @PutMapping
    public Result<UserConfigVO> save(
            @Valid @RequestBody ModelConfigUpdateRequest request) {

        return Result.success(
                service.save(
                        platformOwner.requirePlatformUserId(),
                        request
                )
        );
    }

    @DeleteMapping("/keys")
    public Result<UserConfigVO> clearKeys(
            @RequestParam(defaultValue = "ALL")
            String target) {

        return Result.success(
                service.clearKeys(
                        platformOwner.requirePlatformUserId(),
                        target
                )
        );
    }
}
