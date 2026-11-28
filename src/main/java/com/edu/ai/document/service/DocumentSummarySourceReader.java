package com.edu.ai.document.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class DocumentSummarySourceReader {

    private final JdbcTemplate jdbc;

    public DocumentSummarySourceReader(
            JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SourceChunk(
            Integer page,
            Integer chunkIndex,
            String sectionTitle,
            String content
    ) {}

    /**
     * 读取指定课程资料的全部知识切片。
     */
    public List<SourceChunk> read(
            Long courseId,
            Long documentId) {

        return jdbc.query(
                """
                SELECT
                    page_no,
                    chunk_index,
                    section_title,
                    content

                FROM course_document_chunk

                WHERE course_id = ?
                  AND document_id = ?

                ORDER BY
                    page_no ASC,
                    chunk_index ASC
                """,

                (rs, rowNum) -> new SourceChunk(
                        rs.getInt("page_no"),
                        rs.getInt("chunk_index"),
                        rs.getString("section_title"),
                        rs.getString("content")
                ),

                courseId,
                documentId
        );
    }

    /**
     * 计算切片内容指纹。
     *
     * 页码、切片顺序、章节标题、正文都参与计算。
     */
    public String fingerprint(
            List<SourceChunk> chunks) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            for (SourceChunk chunk : chunks) {

                update(digest, chunk.page().toString());

                update(digest, chunk.chunkIndex().toString());

                update(digest, chunk.sectionTitle());

                update(digest, chunk.content());
            }

            return HexFormat.of()
                    .formatHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "无法计算资料内容指纹",
                    e
            );
        }
    }

    private void update(
            MessageDigest digest,
            String value) {

        byte[] bytes = (
                value == null ? "" : value
        ).getBytes(StandardCharsets.UTF_8);

        digest.update(bytes);

        // 使用长度分隔，避免不同字段组合产生歧义。
        digest.update((byte) 0);
    }

    /**
     * 计算资料总字符数。
     */
    public int totalCharacters(
            List<SourceChunk> chunks) {

        long total = chunks.stream()
                .mapToLong(chunk ->
                        chunk.content() == null
                                ? 0
                                : chunk.content().length()
                )
                .sum();

        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "资料内容过长"
            );
        }

        return (int) total;
    }
}
