USE edu_ai;

-- =====================================================
-- F5-03：错题重练答题记录
-- =====================================================

CREATE TABLE IF NOT EXISTS course_exercise_retry_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '重练记录ID',

    retry_list_id BIGINT NOT NULL COMMENT '重练清单ID',

    student_id BIGINT NOT NULL COMMENT '学生ID',

    attempt_no INT NOT NULL DEFAULT 1 COMMENT '重练次数',

    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS'
        COMMENT 'IN_PROGRESS、PENDING_SELF_REVIEW、GRADED',

    total_score DECIMAL(10,2) NOT NULL DEFAULT 0.00,

    earned_score DECIMAL(10,2) NOT NULL DEFAULT 0.00,

    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    submitted_at DATETIME DEFAULT NULL,

    graded_at DATETIME DEFAULT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_retry_attempt_no (
        retry_list_id,
        student_id,
        attempt_no
    ),

    KEY idx_retry_attempt_status (
        retry_list_id,
        student_id,
        status
    ),

    KEY idx_retry_attempt_student (
        student_id,
        created_at
    )

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='学生错题重练记录';


-- =====================================================
-- F5-03：重练答题明细
-- =====================================================

CREATE TABLE IF NOT EXISTS course_exercise_retry_attempt_answer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '答题明细ID',

    attempt_id BIGINT NOT NULL COMMENT '重练记录ID',

    retry_item_id BIGINT NOT NULL COMMENT '重练清单明细ID',

    question_id BIGINT NOT NULL COMMENT '原题目ID',

    student_answer TEXT COMMENT '学生答案',

    is_correct TINYINT DEFAULT NULL
        COMMENT '是否正确：1正确、0错误、NULL待自我核对',

    score_earned DECIMAL(10,2) NOT NULL DEFAULT 0.00
        COMMENT '本题得分',

    pending_self_review TINYINT NOT NULL DEFAULT 0
        COMMENT '是否等待学生自我核对',

    self_review_understood TINYINT DEFAULT NULL
        COMMENT '学生自我核对结果：1已掌握、0仍需巩固、NULL未核对',

    self_reviewed_at DATETIME DEFAULT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_retry_attempt_item (
        attempt_id,
        retry_item_id
    ),

    KEY idx_retry_answer_attempt (attempt_id),

    KEY idx_retry_answer_question (question_id)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='学生错题重练答题明细';
