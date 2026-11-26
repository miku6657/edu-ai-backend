-- F2-04：知识库发布审核数据库迁移
--
-- 在 MySQL（项目使用 edu_ai 库）中执行本文件即可。
-- 执行顺序：
--   1. 为 course_document 增加发布审核字段
--   2. 回填存量已发布数据
--   3. 新建审核历史表
--
-- 依赖表：course_document(id)

USE edu_ai;


-- 1. 为 course_document 增加发布审核字段
ALTER TABLE course_document

    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        COMMENT '发布审核状态：DRAFT待提交/PENDING待审核/APPROVED通过/REJECTED驳回',

    ADD COLUMN review_submitter_id BIGINT NULL
        COMMENT '最近一次提交审核的用户ID',

    ADD COLUMN review_submit_time DATETIME NULL
        COMMENT '最近一次提交审核的时间',

    ADD COLUMN reviewer_id BIGINT NULL
        COMMENT '最近一次审核的用户ID',

    ADD COLUMN review_time DATETIME NULL
        COMMENT '最近一次审核时间',

    ADD COLUMN review_comment VARCHAR(1000) NULL
        COMMENT '最近一次审核意见';


-- 2. 回填存量已发布数据
--
-- 旧逻辑中，索引完成后自动生效，READY + CURRENT/HISTORY
-- 的文档均已正式发布。此处将其标记为 APPROVED，
-- 避免迁移后这些文档被排除在 RAG 检索范围之外。
UPDATE course_document
SET review_status = 'APPROVED'
WHERE status = 'READY'
  AND version_status IN ('CURRENT', 'HISTORY');


-- 3. 新建发布审核历史表
CREATE TABLE IF NOT EXISTS course_document_review_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL
        COMMENT 'SUBMIT提交/APPROVE通过/REJECT驳回/WITHDRAW撤回',
    previous_status VARCHAR(20) NULL,
    next_status VARCHAR(20) NULL,
    comment VARCHAR(1000) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    KEY idx_review_doc (course_id, document_id, id)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4;
