package com.edu.ai.system.enums;

import java.util.Arrays;
import java.util.Locale;

public enum UserRole {

    ADMIN,
    TEACHER,
    STUDENT;

    public static boolean isValid(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }

        return Arrays.stream(values())
                .anyMatch(value ->
                        value.name().equals(
                                role.trim().toUpperCase(Locale.ROOT)
                        )
                );
    }

    public static String normalize(String role) {
        if (!isValid(role)) {
            throw new IllegalArgumentException("用户角色不合法");
        }

        return role.trim().toUpperCase(Locale.ROOT);
    }
}
