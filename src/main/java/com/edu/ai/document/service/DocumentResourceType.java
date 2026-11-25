package com.edu.ai.document.service;

import com.edu.ai.common.BusinessException;

import java.util.Locale;

public final class DocumentResourceType {

    public static final String LESSON_PLAN = "LESSON_PLAN";
    public static final String COURSEWARE = "COURSEWARE";
    public static final String EXERCISE = "EXERCISE";
    public static final String PRACTICAL_GUIDE = "PRACTICAL_GUIDE";
    public static final String OTHER = "OTHER";

    private DocumentResourceType() {}

    /**
     * 教师明确选择类型时优先采用；
     * 未指定时，根据文件名自动识别。
     */
    public static String resolve(
            String requestedType,
            String filename) {

        if (requestedType == null
                || requestedType.isBlank()
                || "AUTO".equalsIgnoreCase(
                        requestedType.trim())) {

            return detect(filename);
        }

        String type = requestedType
                .trim()
                .toUpperCase(Locale.ROOT);

        return switch (type) {
            case LESSON_PLAN,
                 COURSEWARE,
                 EXERCISE,
                 PRACTICAL_GUIDE,
                 OTHER -> type;

            default -> throw new BusinessException(
                    400, "不支持的课程资源类型"
            );
        };
    }

    /**
     * 根据 PDF 文件名识别资源类型。
     * 优先匹配更具体的实训、习题类型。
     */
    public static String detect(String filename) {

        if (filename == null || filename.isBlank()) {
            return OTHER;
        }

        String name = filename
                .toLowerCase(Locale.ROOT);

        if (containsAny(
                name,
                "实训",
                "实验指导",
                "实验手册",
                "上机指导",
                "practical",
                "lab guide")) {

            return PRACTICAL_GUIDE;
        }

        if (containsAny(
                name,
                "习题",
                "练习题",
                "题库",
                "试题",
                "作业题",
                "exercise",
                "worksheet")) {

            return EXERCISE;
        }

        if (containsAny(
                name,
                "教案",
                "教学设计",
                "lesson plan",
                "teaching plan")) {

            return LESSON_PLAN;
        }

        if (containsAny(
                name,
                "课件",
                "讲义",
                "ppt",
                "slide",
                "courseware",
                "lecture")) {

            return COURSEWARE;
        }

        return OTHER;
    }

    private static boolean containsAny(
            String text,
            String... keywords) {

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }
}
