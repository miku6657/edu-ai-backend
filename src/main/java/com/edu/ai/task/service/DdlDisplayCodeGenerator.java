package com.edu.ai.task.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class DdlDisplayCodeGenerator {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 生成 DDL 任务编号。
     *
     * 格式：
     * DDL + 时间戳 + 8位随机字符
     *
     * 示例：
     * DDL202611142253A8F31C2D
     */
    public String generate() {

        String timestamp =
                LocalDateTime.now().format(FORMATTER);

        String random =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase();

        return "DDL" + timestamp + random;
    }
}
