
package com.edu.ai.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.mapper.UserMapper;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public CustomUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        // 根据用户名查询用户
        LambdaQueryWrapper<User> queryWrapper =
                new LambdaQueryWrapper<>();

        queryWrapper.eq(User::getUsername, username);

        User user = userMapper.selectOne(queryWrapper);

        // 用户不存在
        if (user == null) {
            throw new UsernameNotFoundException(
                    "用户不存在：" + username
            );
        }

        // 返回 Spring Security 用户对象
        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getRole(),
                user.getStatus() != null
                        && user.getStatus() == 1
        );
    }
}
