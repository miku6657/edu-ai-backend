package com.edu.ai.system.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.mapper.UserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class UserInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserInitializer(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        // 只初始化管理员账号
        createUser(
                "admin01",
                "ADMIN",
                "ADMIN_INIT_PASSWORD"
        );
    }

    private void createUser(
            String username,
            String role,
            String passwordEnvName) {

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );

        // 已存在则跳过，避免重复创建
        if (count > 0) {
            return;
        }

        String password = System.getenv(passwordEnvName);

        // 没有配置密码时不创建账号
        if (password == null || password.isBlank()) {
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(username);
        user.setRole(role);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
    }
}
