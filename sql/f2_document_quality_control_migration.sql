-- F2-05：文档质量控制（暂停/恢复检索）数据库迁移
--
-- 在 MySQL（项目使用 edu_ai 库）中执行本文件即可。
-- 依赖表：course_document(id)

USE edu_ai;


-- 1. course_document 增加质量控制字段
--
-- quality_status 使用 NOT NULL DEFAULT 'NORMAL'，
-- 存量已发布资料会自动回填为 NORMAL，避免被 RAG 检索白名单排除。
ALTER TABLE course_document

    ADD COLUMN quality_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        COMMENT '知识库质量状态：NORMAL正常/SUSPENDED暂停检索',

    ADD COLUMN quality_block_reason VARCHAR(1000) NULL
        COMMENT '暂停检索原因',

    ADD COLUMN quality_blocked_by BIGINT NULL
        COMMENT '执行暂停操作的用户ID',

    ADD COLUMN quality_blocked_at DATETIME NULL
        COMMENT '暂停检索时间';


-- 2. 质量控制操作日志表
CREATE TABLE IF NOT EXISTS course_document_quality_control_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL
        COMMENT 'SUSPEND暂停/RESUME恢复',
    previous_status VARCHAR(20) NULL,
    next_status VARCHAR(20) NULL,
    reason VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    KEY idx_qc_log_doc (document_id, id)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4;
