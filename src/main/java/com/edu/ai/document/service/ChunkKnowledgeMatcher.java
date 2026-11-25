package com.edu.ai.document.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ChunkKnowledgeMatcher {

    private static final int MIN_SCORE = 30;

    private static final int MAX_CANDIDATES = 5;

    private static final Set<String> STOP_WORDS =
            Set.of(
                    "java",
                    "class",
                    "public",
                    "private",
                    "static",
                    "void",
                    "null",
                    "true",
                    "false",
                    "课程",
                    "知识点",
                    "学习",
                    "内容",
                    "基础",
                    "介绍"
            );

    /**
     * 知识点候选信息。
     */
    public record PointCandidate(

            Long id,

            Long chapterId,

            String title,

            String matchKeywords

    ) {}

    /**
     * 待匹配知识切片。
     */
    public record ChunkInput(

            Long chunkId,

            Long chapterId,

            String sectionTitle,

            String content

    ) {}

    /**
     * 推荐结果。
     */
    public record MatchResult(

            Long pointId,

            String pointTitle,

            int score,

            String reason

    ) {}

    /**
     * 为单个知识切片推荐候选知识点。
     */
    public List<MatchResult> recommend(
            ChunkInput chunk,
            List<PointCandidate> candidates,
            Set<Long> documentPointIds) {

        if (chunk == null
                || candidates == null
                || candidates.isEmpty()) {

            return List.of();
        }

        if (chunk.content() == null
                || chunk.content().isBlank()) {

            return List.of();
        }

        Set<Long> existingPointIds =
                documentPointIds == null
                        ? Set.of()
                        : documentPointIds;

        List<MatchResult> results =
                new ArrayList<>();

        for (PointCandidate point : candidates) {

            if (point == null
                    || point.id() == null) {

                continue;
            }

            // 已指定章节的切片，
            // 不推荐明确属于其他章节的知识点。
            // 未分类知识点仍允许参与候选匹配。
            if (chunk.chapterId() != null
                    && point.chapterId() != null
                    && !Objects.equals(
                            chunk.chapterId(),
                            point.chapterId()
                    )) {

                continue;
            }

            MatchResult result = calculateScore(
                    chunk,
                    point,
                    existingPointIds.contains(
                            point.id()
                    )
            );

            if (result != null
                    && result.score() >= MIN_SCORE) {

                results.add(result);
            }
        }

        return results.stream()

                .sorted(
                        Comparator
                                .comparingInt(
                                        MatchResult::score
                                )
                                .reversed()
                                .thenComparing(
                                        MatchResult::pointId
                                )
                )

                .limit(MAX_CANDIDATES)

                .toList();
    }

    /**
     * 计算单个知识点的推荐得分。
     */
    private MatchResult calculateScore(
            ChunkInput chunk,
            PointCandidate point,
            boolean documentLinked) {

        String content =
                normalize(chunk.content());

        String sectionTitle =
                normalize(chunk.sectionTitle());

        String pointTitle =
                normalize(point.title());

        if (!validKeyword(pointTitle)) {

            return null;
        }

        List<String> reasons =
                new ArrayList<>();

        int score = 0;

        boolean contentMatched = false;

        // 1. 正文命中知识点名称。
        if (containsKeyword(
                content,
                pointTitle
        )) {

            score += 45;
            contentMatched = true;

            reasons.add(
                    "正文命中知识点名称"
            );
        }

        // 2. 原文章节标题命中知识点名称。
        if (containsKeyword(
                sectionTitle,
                pointTitle
        )) {

            score += 25;
            contentMatched = true;

            reasons.add(
                    "原文章节命中知识点名称"
            );
        }

        // 3. 解析知识点匹配关键词。
        Set<String> keywords =
                parseKeywords(
                        point.matchKeywords()
                );

        // 名称和同名关键词不重复计分。
        keywords.remove(pointTitle);

        List<String> contentHits =
                new ArrayList<>();

        List<String> sectionHits =
                new ArrayList<>();

        for (String keyword : keywords) {

            if (containsKeyword(
                    content,
                    keyword
            )) {

                contentHits.add(keyword);
            }

            if (containsKeyword(
                    sectionTitle,
                    keyword
            )) {

                sectionHits.add(keyword);
            }
        }

        if (!contentHits.isEmpty()) {

            score += Math.min(
                    30,
                    contentHits.size() * 10
            );

            contentMatched = true;

            reasons.add(
                    "正文关键词："
                    + String.join(
                            "、",
                            contentHits
                    )
            );
        }

        if (!sectionHits.isEmpty()) {

            score += Math.min(
                    10,
                    sectionHits.size() * 5
            );

            contentMatched = true;

            reasons.add(
                    "章节关键词："
                    + String.join(
                            "、",
                            sectionHits
                    )
            );
        }

        // 未命中任何有效文本，
        // 不允许只依靠章节、文档关系进行推荐。
        if (!contentMatched) {

            return null;
        }

        // 4. 同章节增加得分。
        if (chunk.chapterId() != null
                && Objects.equals(
                        chunk.chapterId(),
                        point.chapterId()
                )) {

            score += 15;

            reasons.add(
                    "属于同一课程章节"
            );
        }

        // 5. 原始文档已挂接该知识点。
        if (documentLinked) {

            score += 10;

            reasons.add(
                    "原始文档已关联该知识点"
            );
        }

        score = Math.min(
                score,
                100
        );

        String reason =
                String.join(
                        "；",
                        reasons
                );

        if (reason.length() > 1000) {

            reason = reason.substring(
                    0,
                    1000
            );
        }

        return new MatchResult(
                point.id(),
                point.title(),
                score,
                reason
        );
    }

    /**
     * 兼容中英文逗号、分号及换行。
     */
    private Set<String> parseKeywords(
            String raw) {

        Set<String> result =
                new LinkedHashSet<>();

        if (raw == null
                || raw.isBlank()) {

            return result;
        }

        for (String item :
                raw.split("[,，;；、\\n\\r]+")) {

            String keyword =
                    normalize(item);

            if (validKeyword(keyword)) {

                result.add(keyword);
            }
        }

        return result;
    }

    private boolean validKeyword(
            String keyword) {

        return keyword != null
                && keyword.length() >= 2
                && keyword.length() <= 200
                && !STOP_WORDS.contains(keyword);
    }

    private String normalize(
            String text) {

        if (text == null) {

            return "";
        }

        return text
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    /**
     * 英文关键词使用单词边界匹配，
     * 中文关键词使用包含匹配。
     */
    private boolean containsKeyword(
            String text,
            String keyword) {

        if (text == null
                || text.isBlank()
                || !validKeyword(keyword)) {

            return false;
        }

        if (keyword.matches(
                "[a-z0-9_]+"
        )) {

            Pattern pattern = Pattern.compile(
                    "(?<![a-z0-9_])"
                    + Pattern.quote(keyword)
                    + "(?![a-z0-9_])"
            );

            return pattern
                    .matcher(text)
                    .find();
        }

        return text.contains(keyword);
    }
}
