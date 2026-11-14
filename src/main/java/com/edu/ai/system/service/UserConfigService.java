package com.edu.ai.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.system.dto.PreferencesUpdateRequest;
import com.edu.ai.system.dto.UserConfigVO;
import com.edu.ai.system.entity.User;
import com.edu.ai.system.entity.UserConfig;
import com.edu.ai.system.mapper.UserConfigMapper;
import com.edu.ai.system.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

@Service
public class UserConfigService {

    private static final Set<String> IM_PLATFORMS =
            Set.of("QQ_ONEBOT", "WECOM");

    private final UserMapper userMapper;
    private final UserConfigMapper configMapper;
    private final CourseMapper courseMapper;

    public UserConfigService(
            UserMapper userMapper,
            UserConfigMapper configMapper,
            CourseMapper courseMapper) {

        this.userMapper = userMapper;
        this.configMapper = configMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 查询用户配置。
     * 首次查询时返回真实的未配置状态与默认偏好。
     */
    public UserConfigVO getConfig(Long userId) {

        User user = requireUser(userId);
        UserConfig config = findConfig(userId);

        if (config == null) {
            config = newDefaultConfig(userId);
        }

        return toVO(user, config);
    }

    /**
     * 修改个人偏好。
     * 对 sys_user 行加锁，避免首次创建配置时并发插入。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserConfigVO updatePreferences(
            Long userId,
            PreferencesUpdateRequest request) {

        if (request == null) {
            throw new BusinessException(
                    400, "请求参数不能为空");
        }

        String timezone = request.getTimezone();

        try {
            ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException e) {
            throw new BusinessException(
                    400, "用户时区不合法");
        }

        LocalTime startTime;
        LocalTime endTime;

        try {
            startTime = LocalTime.parse(
                    request.getDndStartTime());
            endTime = LocalTime.parse(
                    request.getDndEndTime());
        } catch (DateTimeException | NullPointerException e) {
            throw new BusinessException(
                    400, "免打扰时间格式不合法");
        }

        String imPlatform = normalize(
                request.getBoundImPlatform());
        String imUid = normalize(
                request.getBoundImUid());

        if ((imPlatform == null) != (imUid == null)) {
            throw new BusinessException(
                    400,
                    "IM平台和账号必须同时填写或同时为空"
            );
        }

        if (imPlatform != null
                && !IM_PLATFORMS.contains(imPlatform)) {
            throw new BusinessException(
                    400, "不支持的IM平台");
        }

        if (imUid != null && imUid.length() > 150) {
            throw new BusinessException(
                    400, "IM账号长度不能超过150");
        }

        // 所有用户配置写操作统一先锁定用户行。
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getId, userId)
                        .eq(User::getStatus, 1)
                        .last("FOR UPDATE")
        );

        if (user == null) {
            throw new BusinessException(
                    404, "用户不存在或已禁用");
        }

        UserConfig config = findConfig(userId);
        LocalDateTime now = LocalDateTime.now();

        if (config == null) {
            config = newDefaultConfig(userId);

            config.setTimezone(timezone);
            config.setDndStartTime(startTime);
            config.setDndEndTime(endTime);
            config.setBoundImPlatform(imPlatform);
            config.setBoundImUid(imUid);
            config.setCreateTime(now);
            config.setUpdateTime(now);

            configMapper.insert(config);
        } else {

            // UpdateWrapper 可明确把 IM 字段设置为 NULL。
            int updated = configMapper.update(
                    null,
                    new LambdaUpdateWrapper<UserConfig>()
                            .eq(UserConfig::getId, config.getId())
                            .eq(UserConfig::getUserId, userId)
                            .set(UserConfig::getTimezone, timezone)
                            .set(UserConfig::getDndStartTime, startTime)
                            .set(UserConfig::getDndEndTime, endTime)
                            .set(UserConfig::getBoundImPlatform, imPlatform)
                            .set(UserConfig::getBoundImUid, imUid)
                            .set(UserConfig::getUpdateTime, now)
            );

            if (updated != 1) {
                throw new BusinessException(
                        409, "配置已发生变化，请重试");
            }

            config = findConfig(userId);
        }

        return toVO(user, config);
    }

    private User requireUser(Long userId) {

        if (userId == null || userId <= 0) {
            throw new BusinessException(
                    401, "用户登录状态无效");
        }

        User user = userMapper.selectById(userId);

        if (user == null
                || !Integer.valueOf(1).equals(
                        user.getStatus())) {
            throw new BusinessException(
                    404, "用户不存在或已禁用");
        }

        return user;
    }

    private UserConfig findConfig(Long userId) {

        return configMapper.selectOne(
                new LambdaQueryWrapper<UserConfig>()
                        .eq(UserConfig::getUserId, userId)
        );
    }

    private UserConfig newDefaultConfig(Long userId) {

        UserConfig config = new UserConfig();

        config.setUserId(userId);
        config.setTimezone("Asia/Shanghai");
        config.setDndStartTime(LocalTime.of(23, 30));
        config.setDndEndTime(LocalTime.of(7, 30));
        config.setQqbotStatus("DISCONNECTED");
        config.setQqbotBound(false);

        LocalDateTime now = LocalDateTime.now();
        config.setCreateTime(now);
        config.setUpdateTime(now);

        return config;
    }

    private UserConfigVO toVO(
            User user,
            UserConfig config) {

        long courseCount = courseMapper.selectCount(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getUserId, user.getId())
        );

        return UserConfigVO.from(
                user, config, courseCount);
    }

    private String normalize(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
