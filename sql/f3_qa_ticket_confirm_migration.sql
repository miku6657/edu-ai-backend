-- F3：无依据拒答改为学生确认后创建工单 —— chat_message 表补充拒答标记列
--
-- 在 MySQL（项目使用 edu_ai 库）中执行本文件即可。
-- 依赖表：chat_message

USE edu_ai;

ALTER TABLE chat_message
    ADD COLUMN no_evidence_reason VARCHAR(40) NULL
        COMMENT 'AI拒答原因，仅拒答的assistant消息填写',
    ADD COLUMN question_message_id BIGINT NULL
        COMMENT '拒答回答对应的学生问题ID',
    ADD KEY idx_chat_message_question (question_message_id);
