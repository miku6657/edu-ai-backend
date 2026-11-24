package com.edu.ai.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.course.entity.Course;
import com.edu.ai.course.mapper.CourseMapper;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CourseInviteCodeGenerator {

    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    private final CourseMapper courseMapper;

    public CourseInviteCodeGenerator(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    public String generate() {

        for (int attempt = 0; attempt < 20; attempt++) {

            StringBuilder code = new StringBuilder();

            for (int i = 0; i < CODE_LENGTH; i++) {

                code.append(
                        ALPHABET.charAt(
                                random.nextInt(ALPHABET.length())
                        )
                );
            }

            String inviteCode = code.toString();

            Long count = courseMapper.selectCount(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getInviteCode, inviteCode)
            );

            if (count == null || count == 0) {
                return inviteCode;
            }
        }

        throw new IllegalStateException(
                "生成课程邀请码失败，请重试"
        );
    }
}
