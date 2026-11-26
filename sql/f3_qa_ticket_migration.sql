-- F3-04：无依据问答转人工工单数据库迁移
--
-- 在 MySQL（项目使用 edu_ai 库）中执行本文件即可。
-- 依赖表：sys_user(id)、course(id)、course_student(student_id, course_id)

USE edu_ai;

CREATE TABLE IF NOT EXISTS course_qa_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    question_message_id BIGINT NOT NULL,
    question_text VARCHAR(2000) NOT NULL,
    no_evidence_reason VARCHAR(40) NOT NULL
        COMMENT 'NO_ACTIVE_DOCUMENTS/NO_RETRIEVAL_MATCH',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING待答复/ANSWERED已答复',
    teacher_reply VARCHAR(5000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    answered_at DATETIME(3) NULL,

    UNIQUE KEY uk_qa_ticket_question (question_message_id),
    KEY idx_qa_ticket_student (student_id, status, id),
    KEY idx_qa_ticket_teacher (teacher_id, status, id),
    KEY idx_qa_ticket_course (course_id, status, id)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4;
