USE edu_ai;

-- F5-04：学生错题 AI 讲解会话

CREATE TABLE IF NOT EXISTS course_exercise_explain_session (

    id BIGINT NOT NULL AUTO_INCREMENT,

    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,

    chat_session_id BIGINT NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_student_course_question (
        student_id,
        course_id,
        question_id
    ),

    UNIQUE KEY uk_explain_chat_session (
        chat_session_id
    ),

    KEY idx_explain_student (
        student_id,
        created_at
    )

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='学生错题AI讲解会话关联';
