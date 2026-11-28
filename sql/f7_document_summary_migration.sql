USE edu_ai;

CREATE TABLE IF NOT EXISTS course_document_summary (

    id BIGINT NOT NULL AUTO_INCREMENT,

    course_id BIGINT NOT NULL,

    document_id BIGINT NOT NULL,

    revision INT NOT NULL DEFAULT 1,

    summary LONGTEXT NOT NULL,

    outline_json LONGTEXT NOT NULL,

    source_fingerprint CHAR(64) NOT NULL,

    source_chunk_count INT NOT NULL DEFAULT 0,

    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT/CONFIRMED/ARCHIVED/SUPERSEDED',

    generated_by BIGINT NOT NULL,

    generated_at DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    confirmed_by BIGINT DEFAULT NULL,

    confirmed_at DATETIME DEFAULT NULL,

    create_time DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    update_time DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_document_summary_revision (
        document_id,
        revision
    ),

    KEY idx_summary_course_document (
        course_id,
        document_id,
        status
    ),

    KEY idx_summary_generated (
        generated_by,
        generated_at
    )

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COMMENT='课程资料AI摘要与章节大纲';
