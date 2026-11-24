package com.edu.ai.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.common.BusinessException;

import com.edu.ai.course.dto.CourseMemberVO;
import com.edu.ai.course.entity.Course;
import com.edu.ai.course.entity.CourseStudent;
import com.edu.ai.course.mapper.CourseMapper;
import com.edu.ai.course.mapper.CourseStudentMapper;

import com.edu.ai.system.entity.User;
import com.edu.ai.system.mapper.UserMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CourseMembershipService {

    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final UserMapper userMapper;
    private final CourseInviteCodeGenerator inviteCodeGenerator;

    public CourseMembershipService(
            CourseMapper courseMapper,
            CourseStudentMapper courseStudentMapper,
            UserMapper userMapper,
            CourseInviteCodeGenerator inviteCodeGenerator) {

        this.courseMapper = courseMapper;
        this.courseStudentMapper = courseStudentMapper;
        this.userMapper = userMapper;
        this.inviteCodeGenerator = inviteCodeGenerator;
    }

    /**
     * 学生通过邀请码加入课程
     */
    @Transactional(rollbackFor = Exception.class)
    public Course joinByInviteCode(
            Long studentId,
            String inviteCode) {

        User student = userMapper.selectById(studentId);

        if (student == null
                || !Integer.valueOf(1).equals(student.getStatus())) {

            throw new BusinessException(
                    401,
                    "学生账号不存在或已被禁用"
            );
        }

        if (!"STUDENT".equals(student.getRole())) {
            throw new BusinessException(
                    403,
                    "只有学生账号可以加入课程"
            );
        }

        String code = inviteCode == null
                ? ""
                : inviteCode.trim().toUpperCase(Locale.ROOT);

        if (code.isBlank()) {
            throw new BusinessException(400, "邀请码不能为空");
        }

        Course course = courseMapper.selectOne(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getInviteCode, code)
        );

        if (course == null) {
            throw new BusinessException(
                    404,
                    "课程邀请码不存在或已失效"
            );
        }

        if (!Integer.valueOf(1).equals(course.getStatus())) {
            throw new BusinessException(
                    409,
                    "课程已停用，无法加入"
            );
        }

        Long count = courseStudentMapper.selectCount(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, course.getId())
                        .eq(CourseStudent::getStudentId, studentId)
        );

        if (count != null && count > 0) {
            throw new BusinessException(
                    409,
                    "你已经加入该课程"
            );
        }

        CourseStudent relation = new CourseStudent();

        relation.setCourseId(course.getId());
        relation.setStudentId(studentId);
        relation.setJoinTime(LocalDateTime.now());

        try {
            courseStudentMapper.insert(relation);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    409,
                    "你已经加入该课程"
            );
        }

        return course;
    }

    /**
     * 查询学生加入的课程
     */
    public List<Course> getJoinedCourses(Long studentId) {

        List<CourseStudent> relations =
                courseStudentMapper.selectList(
                        new LambdaQueryWrapper<CourseStudent>()
                                .eq(
                                        CourseStudent::getStudentId,
                                        studentId
                                )
                                .orderByDesc(
                                        CourseStudent::getJoinTime
                                )
                );

        if (relations.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = relations.stream()
                .map(CourseStudent::getCourseId)
                .distinct()
                .toList();

        return courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .in(Course::getId, courseIds)
                        .eq(Course::getStatus, 1)
                        .orderByDesc(Course::getCreateTime)
        );
    }

    /**
     * 查询课程详情
     * ADMIN：全局访问
     * TEACHER：只能访问自己创建的课程
     * STUDENT：只能访问自己加入的课程
     */
    public Course getCourseDetail(
            Long courseId,
            Long userId,
            String role) {

        Course course = requireCourse(courseId);

        if ("ADMIN".equals(role)) {
            return course;
        }

        if ("TEACHER".equals(role)
                && userId.equals(course.getUserId())) {
            return course;
        }

        if ("STUDENT".equals(role)
                && Integer.valueOf(1).equals(course.getStatus())
                && isMember(courseId, userId)) {
            return course;
        }

        throw new BusinessException(
                403,
                "你没有访问该课程的权限"
        );
    }

    /**
     * 查看课程学生名单
     */
    public List<CourseMemberVO> getCourseMembers(
            Long courseId,
            Long userId,
            String role) {

        requireCourseManager(courseId, userId, role);

        List<CourseStudent> relations =
                courseStudentMapper.selectList(
                        new LambdaQueryWrapper<CourseStudent>()
                                .eq(
                                        CourseStudent::getCourseId,
                                        courseId
                                )
                                .orderByDesc(
                                        CourseStudent::getJoinTime
                                )
                );

        if (relations.isEmpty()) {
            return List.of();
        }

        List<Long> studentIds = relations.stream()
                .map(CourseStudent::getStudentId)
                .distinct()
                .toList();

        Map<Long, User> userMap = new HashMap<>();

        userMapper.selectByIds(studentIds)
                .forEach(user ->
                        userMap.put(user.getId(), user)
                );

        List<CourseMemberVO> result = new ArrayList<>();

        for (CourseStudent relation : relations) {

            User user = userMap.get(relation.getStudentId());

            if (user == null) {
                continue;
            }

            CourseMemberVO vo = new CourseMemberVO();

            vo.setStudentId(String.valueOf(user.getId()));
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setJoinTime(relation.getJoinTime());

            result.add(vo);
        }

        return result;
    }

    /**
     * 将学生移出课程
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeStudent(
            Long courseId,
            Long studentId,
            Long operatorId,
            String role) {

        requireCourseManager(courseId, operatorId, role);

        int deleted = courseStudentMapper.delete(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(
                                CourseStudent::getCourseId,
                                courseId
                        )
                        .eq(
                                CourseStudent::getStudentId,
                                studentId
                        )
        );

        if (deleted == 0) {
            throw new BusinessException(
                    404,
                    "该学生不在课程中"
            );
        }
    }

    /**
     * 查看邀请码
     * 旧课程没有邀请码时自动补生成
     */
    @Transactional(rollbackFor = Exception.class)
    public String getInviteCode(
            Long courseId,
            Long userId,
            String role) {

        Course course = requireCourseManager(
                courseId,
                userId,
                role
        );

        if (course.getInviteCode() == null
                || course.getInviteCode().isBlank()) {

            course.setInviteCode(
                    inviteCodeGenerator.generate()
            );

            course.setUpdateTime(LocalDateTime.now());

            courseMapper.updateById(course);
        }

        return course.getInviteCode();
    }

    /**
     * 重新生成邀请码
     * 旧邀请码立即失效
     */
    @Transactional(rollbackFor = Exception.class)
    public String regenerateInviteCode(
            Long courseId,
            Long userId,
            String role) {

        Course course = requireCourseManager(
                courseId,
                userId,
                role
        );

        course.setInviteCode(
                inviteCodeGenerator.generate()
        );

        course.setUpdateTime(LocalDateTime.now());

        courseMapper.updateById(course);

        return course.getInviteCode();
    }

    /**
     * 校验课程管理权限
     */
    private Course requireCourseManager(
            Long courseId,
            Long userId,
            String role) {

        Course course = requireCourse(courseId);

        if ("ADMIN".equals(role)) {
            return course;
        }

        if (!"TEACHER".equals(role)
                || !userId.equals(course.getUserId())) {

            throw new BusinessException(
                    403,
                    "只有课程创建教师或管理员可以执行此操作"
            );
        }

        return course;
    }

    private Course requireCourse(Long courseId) {

        Course course = courseMapper.selectById(courseId);

        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }

        return course;
    }

    private boolean isMember(Long courseId, Long userId) {

        Long count = courseStudentMapper.selectCount(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStudentId, userId)
        );

        return count != null && count > 0;
    }
}
