USE edu_ai;

ALTER TABLE course_document
    ADD COLUMN valid_until DATE NULL
        COMMENT '资料有效截止日期，NULL表示长期有效',

    ADD COLUMN update_responsible_id BIGINT NULL
        COMMENT '资料更新责任人ID，NULL表示课程负责人',

    ADD COLUMN expiry_remind_days INT NOT NULL DEFAULT 7
        COMMENT '资料到期前提醒天数';

CREATE INDEX idx_document_expiry
ON course_document (
    version_status,
    review_status,
    status,
    valid_until
);
