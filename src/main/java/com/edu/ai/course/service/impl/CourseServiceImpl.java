package com.edu.ai.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.ai.common.BusinessException;
import com.edu.ai.course.dto.CourseDTO;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.course.service.CourseInviteCodeGenerator;
import com.edu.ai.course.service.CourseService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class CourseServiceImpl implements CourseService {

    private static final Pattern COURSE_CODE_PATTERN =
            Pattern.compile("^[A-Z0-9_-]{2,32}$");

    private final CourseMapper courseMapper;
    private final CourseInviteCodeGenerator inviteCodeGenerator;

    public CourseServiceImpl(
            CourseMapper courseMapper,
            CourseInviteCodeGenerator inviteCodeGenerator) {

        this.courseMapper = courseMapper;
        this.inviteCodeGenerator = inviteCodeGenerator;
    }

    /**
     * 创建课程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Course createCourse(CourseDTO dto, Long userId) {

        if (userId == null) {
            throw new BusinessException(401, "登录状态无效");
        }

        validateCourseDTO(dto);

        String code = normalizeCode(dto.getCourseCode());

        Course course = new Course();

        course.setCourseCode(code);
        course.setName(dto.getName().trim());
        course.setDescription(trimToNull(dto.getDescription()));
        course.setCredits(dto.getCredits());
        course.setApplicableMajor(
                dto.getApplicableMajor().trim()
        );
        course.setSemester(dto.getSemester().trim());

        course.setUserId(userId);
        course.setStatus(1);

        // 保留原有邀请码机制
        course.setInviteCode(
                inviteCodeGenerator.generate()
        );

        LocalDateTime now = LocalDateTime.now();
        course.setCreateTime(now);
        course.setUpdateTime(now);

        try {
            courseMapper.insert(course);
        } catch (DuplicateKeyException e) {
            // 防并发重复写入，由数据库唯一索引兜底。
            // 邀请码若碰撞也会进入此分支。
            throw new BusinessException(
                    409,
                    "课程编码或邀请码已存在，请检查课程编码后重试"
            );
        }

        return course;
    }

    /**
     * 编辑课程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCourse(
            Long courseId,
            CourseDTO dto,
            Long userId) {

        Course course = requireMyCourse(courseId, userId);

        validateCourseDTO(dto);

        course.setCourseCode(
                normalizeCode(dto.getCourseCode())
        );
        course.setName(dto.getName().trim());
        course.setDescription(
                trimToNull(dto.getDescription())
        );
        course.setCredits(dto.getCredits());
        course.setApplicableMajor(
                dto.getApplicableMajor().trim()
        );
        course.setSemester(dto.getSemester().trim());
        course.setUpdateTime(LocalDateTime.now());

        try {
            courseMapper.updateById(course);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    409,
                    "课程编码已被其他课程使用"
            );
        }
    }

    /**
     * 停用课程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableCourse(Long courseId, Long userId) {

        Course course = requireMyCourse(courseId, userId);
        course.setStatus(0);
        course.setUpdateTime(LocalDateTime.now());

        courseMapper.updateById(course);
    }

    /**
     * 查询当前教师创建的课程
     */
    @Override
    public List<Course> getMyCourses(Long userId) {
        return courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getUserId, userId)
                        .orderByDesc(Course::getCreateTime)
        );
    }

    /**
     * 查询课程详情
     */
    @Override
    public Course getCourseDetail(
            Long courseId,
            Long userId) {

        return requireMyCourse(courseId, userId);
    }

    private Course requireMyCourse(
            Long courseId,
            Long userId) {

        if (userId == null) {
            throw new BusinessException(401, "登录状态无效");
        }

        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        if (!userId.equals(course.getUserId())) {
            throw new BusinessException(403, "无权访问该课程");
        }

        return course;
    }

    /**
     * 验证所有课程基本信息
     */
    private void validateCourseDTO(CourseDTO dto) {

        if (dto == null) {
            throw new BusinessException(
                    400, "课程信息不能为空"
            );
        }

        String code = normalizeCode(dto.getCourseCode());

        if (!COURSE_CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(
                    400,
                    "课程编码只能包含2-32位字母、数字、下划线或横线"
            );
        }

        if (dto.getName() == null
                || dto.getName().isBlank()
                || dto.getName().trim().length() > 100) {

            throw new BusinessException(
                    400,
                    "课程名称不能为空且不能超过100个字符"
            );
        }

        if (dto.getDescription() != null
                && dto.getDescription().length() > 500) {
            throw new BusinessException(
                    400, "课程描述不能超过500个字符"
            );
        }

        BigDecimal credits = dto.getCredits();

        if (credits == null
                || credits.compareTo(BigDecimal.ZERO) <= 0
                || credits.compareTo(
                        new BigDecimal("99.9")
                ) > 0
                || credits.stripTrailingZeros().scale() > 1) {

            throw new BusinessException(
                    400,
                    "学分必须大于0、不超过99.9，且最多保留一位小数"
            );
        }

        if (dto.getApplicableMajor() == null
                || dto.getApplicableMajor().isBlank()
                || dto.getApplicableMajor().trim().length() > 200) {

            throw new BusinessException(
                    400,
                    "适用专业不能为空且不能超过200个字符"
            );
        }

        if (dto.getSemester() == null
                || dto.getSemester().isBlank()
                || dto.getSemester().trim().length() > 50) {

            throw new BusinessException(
                    400,
                    "开课学期不能为空且不能超过50个字符"
            );
        }
    }

    private String normalizeCode(String code) {
        return code == null
                ? ""
                : code.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
