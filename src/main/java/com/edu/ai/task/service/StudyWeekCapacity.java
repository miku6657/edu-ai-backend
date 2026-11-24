package com.edu.ai.task.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class StudyWeekCapacity {

    public static final ZoneId ZONE =
            ZoneId.of("Asia/Shanghai");

    private StudyWeekCapacity() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * 第 weekNo 周的截止日期。
     * 每周从学习路径创建日开始，每7天为一周。
     */
    public static LocalDate dueDate(
            LocalDate start,
            LocalDate target,
            int weekNo) {

        LocalDate due = start
                .plusWeeks(weekNo - 1L)
                .plusDays(6);

        return due.isAfter(target) ? target : due;
    }

    /**
     * 计算指定周的可用学习分钟数。
     * 最后一周不足7天时，按天数比例分配容量。
     */
    public static int capacity(
            LocalDate start,
            LocalDate target,
            int weekNo,
            int weeklyMinutes) {

        if (weekNo < 1 || weeklyMinutes <= 0) {
            return 0;
        }

        LocalDate weekStart =
                start.plusWeeks(weekNo - 1L);

        if (weekStart.isAfter(target)) {
            return 0;
        }

        LocalDate end =
                dueDate(start, target, weekNo);

        long days = ChronoUnit.DAYS.between(
                weekStart, end
        ) + 1;

        return (int) (
                (long) weeklyMinutes * days / 7
        );
    }

    public static int availableWeeks(
            LocalDate start,
            LocalDate target) {

        if (target.isBefore(start)) {
            return 0;
        }

        return (int) (
                ChronoUnit.DAYS.between(start, target) / 7
        ) + 1;
    }
}
