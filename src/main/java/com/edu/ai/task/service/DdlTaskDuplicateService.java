package com.edu.ai.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.edu.ai.task.dto.DdlDuplicateCandidateVO;
import com.edu.ai.task.dto.DdlTaskExtractedData;

import com.edu.ai.task.entity.DdlTask;
import com.edu.ai.task.mapper.DdlTaskMapper;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DdlTaskDuplicateService {

    private static final double SIMILARITY_THRESHOLD = 0.65;

    private final DdlTaskMapper taskMapper;

    public DdlTaskDuplicateService(
            DdlTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 查找当前用户可能重复的任务。
     */
    public List<DdlDuplicateCandidateVO> findCandidates(
            Long userId,
            Long courseId,
            DdlTaskExtractedData extracted) {

        LambdaQueryWrapper<DdlTask> wrapper =
                new LambdaQueryWrapper<DdlTask>()
                        .eq(DdlTask::getUserId, userId)
                        .notIn(
                                DdlTask::getStatus,
                                "COMPLETED",
                                "CANCELED"
                        );

        if (courseId != null) {
            wrapper.eq(DdlTask::getCourseId, courseId);
        }

        List<DdlTask> existingTasks =
                taskMapper.selectList(wrapper);

        List<DdlDuplicateCandidateVO> candidates =
                new ArrayList<>();

        String newTitle = normalize(
                extracted.getTaskTitle()
        );

        for (DdlTask existing : existingTasks) {

            String oldTitle = normalize(
                    existing.getTaskTitle()
            );

            double similarity = calculateSimilarity(
                    newTitle,
                    oldTitle
            );

            if (similarity < SIMILARITY_THRESHOLD) {
                continue;
            }

            DdlDuplicateCandidateVO vo =
                    new DdlDuplicateCandidateVO();

            vo.setTaskId(existing.getId());
            vo.setTaskTitle(existing.getTaskTitle());
            vo.setDueAt(existing.getDueAt());
            vo.setSimilarity(similarity);

            vo.setDuplicateReason(
                    getDuplicateReason(
                            existing,
                            extracted,
                            similarity
                    )
            );

            candidates.add(vo);
        }

        return candidates.stream()
                .sorted(
                        (a, b) -> Double.compare(
                                b.getSimilarity(),
                                a.getSimilarity()
                        )
                )
                .limit(5)
                .toList();
    }

    /**
     * 判断是否属于相同任务。
     */
    public boolean isSameDeadline(
            DdlTask existing,
            DdlTaskExtractedData extracted) {

        LocalDateTime oldTime = existing.getDueAt();
        LocalDateTime newTime = extracted.getDueAt();

        if (oldTime == null || newTime == null) {
            return oldTime == null && newTime == null;
        }

        return oldTime.equals(newTime);
    }

    /**
     * 判断是否需要变更确认。
     */
    public boolean needsChangeConfirmation(
            DdlTask existing,
            DdlTaskExtractedData extracted) {

        if (calculateSimilarity(
                normalize(existing.getTaskTitle()),
                normalize(extracted.getTaskTitle())
        ) < SIMILARITY_THRESHOLD) {

            return false;
        }

        return !isSameDeadline(existing, extracted);
    }

    /**
     * 生成重复原因。
     */
    private String getDuplicateReason(
            DdlTask existing,
            DdlTaskExtractedData extracted,
            double similarity) {

        if (isSameDeadline(existing, extracted)) {
            return "已有任务名称相似，截止时间一致";
        }

        if (needsChangeConfirmation(existing, extracted)) {
            return "已有相似任务，但截止时间或任务信息发生变化";
        }

        return "发现名称相似的任务";
    }

    /**
     * 标题标准化。
     */
    private String normalize(String title) {

        if (title == null) {
            return "";
        }

        return title
                .toLowerCase()
                .replaceAll("[\\p{Punct}\\s，。！？；：、（）【】《》]+", "");
    }

    /**
     * Levenshtein 编辑距离计算相似度。
     */
    private double calculateSimilarity(
            String a,
            String b) {

        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }

        if (a.equals(b)) {
            return 1.0;
        }

        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {

            for (int j = 1; j <= b.length(); j++) {

                int cost = a.charAt(i - 1)
                        == b.charAt(j - 1) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(
                                dp[i - 1][j] + 1,
                                dp[i][j - 1] + 1
                        ),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        int distance = dp[a.length()][b.length()];

        return 1.0 - (double) distance
                / Math.max(a.length(), b.length());
    }
}
