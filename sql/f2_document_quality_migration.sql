-- F2-05：文档质量抽检数据库迁移
--
-- 在 MySQL（项目使用 edu_ai 库）中执行本文件即可。
-- 依赖表：course_document(id)、course_document_chunk(id)、sys_user(id)

USE edu_ai;


-- 1. 抽检任务表
CREATE TABLE IF NOT EXISTS course_document_quality_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    document_name VARCHAR(255) NULL,
    title VARCHAR(200) NOT NULL,
    sample_rate INT NOT NULL,
    total_chunks INT NOT NULL,
    sample_count INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS'
        COMMENT 'IN_PROGRESS进行中/COMPLETED已完成',
    creator_id BIGINT NOT NULL,
    completed_by BIGINT NULL,
    completed_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    KEY idx_task_course_doc (course_id, document_id),
    KEY idx_task_doc_status (document_id, status)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4;


-- 2. 抽检样本表
CREATE TABLE IF NOT EXISTS course_document_quality_sample (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    page_no INT NOT NULL,
    chunk_index INT NOT NULL,
    section_title VARCHAR(500) NULL,
    content TEXT NULL,
    content_hash VARCHAR(64) NULL,
    duplicate_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING待检查/PASS合格/FAIL不合格',
    check_comment VARCHAR(1000) NULL,
    checker_id BIGINT NULL,
    checked_time DATETIME NULL,

    KEY idx_sample_task (task_id),
    KEY idx_sample_task_chunk (task_id, chunk_id)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4;


-- 3. 质量问题表
CREATE TABLE IF NOT EXISTS course_document_quality_issue (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    sample_id BIGINT NOT NULL,
    issue_type VARCHAR(40) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    reporter_id BIGINT NOT NULL,
    assignee_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        COMMENT 'OPEN待整改/RESOLVED待复核/CLOSED已关闭',
    resolution VARCHAR(2000) NULL,
    replacement_document_id BIGINT NULL,
    resolved_time DATETIME NULL,
    reviewer_id BIGINT NULL,
    review_comment VARCHAR(1000) NULL,
    reviewed_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    KEY idx_issue_task (task_id),
    KEY idx_issue_sample (sample_id),
    KEY idx_issue_assignee (assignee_id, status)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4;
