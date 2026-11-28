USE edu_ai;

CREATE TABLE IF NOT EXISTS course_content_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,

    course_id BIGINT NOT NULL,
    course_name VARCHAR(100) NOT NULL,

    document_id BIGINT NULL,
    document_name VARCHAR(255) NULL,

    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(100) NOT NULL,

    operation_type VARCHAR(50) NOT NULL,
    operation_name VARCHAR(100) NOT NULL,

    description VARCHAR(1000) NULL,

    before_value TEXT NULL,
    after_value TEXT NULL,

    create_time DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),

    KEY idx_content_log_course_time (
        course_id,
        create_time,
        id
    ),

    KEY idx_content_log_operator_time (
        operator_id,
        create_time,
        id
    ),

    KEY idx_content_log_type_time (
        operation_type,
        create_time,
        id
    ),

    KEY idx_content_log_document_time (
        document_id,
        create_time,
        id
    )

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COMMENT='课程内容运营统一操作日志';
