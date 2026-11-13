

package com.edu.ai.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.mapper.UserMapper;
import com.edu.ai.system.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
        );
    }

    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public List<User> getUserList() {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .orderByDesc(User::getCreateTime)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addUser(User user) {

        if (getByUsername(user.getUsername()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        LocalDateTime now = LocalDateTime.now();

        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setStatus(1);

        return userMapper.insert(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(User user) {

        if (user.getId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        user.setUpdateTime(LocalDateTime.now());

        return userMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long id) {
        return userMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUserStatus(Long id, Integer status) {

        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("用户状态不合法");
        }

        User user = new User();
        user.setId(id);
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());

        return userMapper.updateById(user) > 0;
    }
}

