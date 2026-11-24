package com.edu.ai.system.service;

import com.edu.ai.common.BusinessException;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PlatformModelOwnerService {

    private final UserMapper userMapper;
    private final Long platformUserId;

    public PlatformModelOwnerService(
            UserMapper userMapper,
            @Value("${ai.platform-config-user-id:0}")
            Long platformUserId) {

        this.userMapper = userMapper;
        this.platformUserId = platformUserId;
    }

    public Long requirePlatformUserId() {

        if (platformUserId == null || platformUserId <= 0) {
            throw new BusinessException(
                    503, "平台尚未配置模型管理员账号"
            );
        }

        User user = userMapper.selectById(platformUserId);

        if (user == null
                || !Integer.valueOf(1).equals(user.getStatus())
                || !"ADMIN".equals(user.getRole())) {

            throw new BusinessException(
                    503, "平台模型管理员账号无效"
            );
        }

        return platformUserId;
    }
}
