USE edu_ai;

CREATE TABLE IF NOT EXISTS course_hot_question (

    id BIGINT NOT NULL AUTO_INCREMENT,

    course_id BIGINT NOT NULL,

    knowledge_point_id BIGINT DEFAULT NULL,

    -- 来源于课程高频提问
    source_question_text VARCHAR(2000) NOT NULL,

    source_question_hash CHAR(64) NOT NULL,

    -- 整理后的标准问答
    question_text VARCHAR(500) NOT NULL,

    answer_text TEXT NOT NULL,

    category VARCHAR(50) NOT NULL DEFAULT '其他',

    -- 创建时保存高频问题统计快照
    question_count BIGINT NOT NULL DEFAULT 0,

    student_count BIGINT NOT NULL DEFAULT 0,

    -- DRAFT/PENDING_REVIEW/PUBLISHED/REJECTED/OFFLINE
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

    created_by BIGINT NOT NULL,

    updated_by BIGINT NOT NULL,

    reviewed_by BIGINT DEFAULT NULL,

    review_comment VARCHAR(1000) DEFAULT NULL,

    submitted_at DATETIME DEFAULT NULL,

    reviewed_at DATETIME DEFAULT NULL,

    published_at DATETIME DEFAULT NULL,

    offlined_at DATETIME DEFAULT NULL,

    sort_order INT NOT NULL DEFAULT 0,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY idx_hot_question_course_status (
        course_id,
        status,
        sort_order
    ),

    KEY idx_hot_question_source (
        course_id,
        source_question_hash
    ),

    KEY idx_hot_question_knowledge (
        course_id,
        knowledge_point_id
    ),

    KEY idx_hot_question_publish (
        course_id,
        status,
        published_at
    )

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COMMENT='课程热门标准问答';
