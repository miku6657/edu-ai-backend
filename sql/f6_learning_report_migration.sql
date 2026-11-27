USE edu_ai;

-- =====================================================
-- F6-03 学生阶段性学习报告
-- =====================================================

-- 1. 学习报告主表
CREATE TABLE IF NOT EXISTS student_learning_report (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '报告ID',

    student_id BIGINT NOT NULL COMMENT '学生ID',
    course_id BIGINT NOT NULL COMMENT '课程ID',

    title VARCHAR(120) NOT NULL COMMENT '报告标题',

    period_start DATE NOT NULL COMMENT '统计开始日期',
    period_end DATE NOT NULL COMMENT '统计结束日期',

    exercise_count INT NOT NULL DEFAULT 0 COMMENT '练习次数',
    objective_answer_count INT NOT NULL DEFAULT 0 COMMENT '已判分客观题数',
    correct_objective_count INT NOT NULL DEFAULT 0 COMMENT '客观题正确数',
    exercise_accuracy_rate DECIMAL(5,2) DEFAULT NULL COMMENT '客观题正确率',

    question_count INT NOT NULL DEFAULT 0 COMMENT '课程提问次数',

    completed_task_count INT NOT NULL DEFAULT 0 COMMENT '完成任务数',
    total_task_count INT NOT NULL DEFAULT 0 COMMENT '有效课程任务数',

    weak_point_count INT NOT NULL DEFAULT 0 COMMENT '薄弱知识点数量',

    report_summary TEXT NOT NULL COMMENT '学习情况总结',
    improvement_suggestions TEXT NOT NULL COMMENT '改进建议',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY idx_learning_report_student (
        student_id, course_id, created_at
    ),

    KEY idx_learning_report_course (
        course_id, created_at
    ),

    CONSTRAINT fk_learning_report_student
        FOREIGN KEY (student_id)
        REFERENCES sys_user(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_learning_report_course
        FOREIGN KEY (course_id)
        REFERENCES course(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_learning_report_period
        CHECK (period_end >= period_start)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='学生阶段性学习报告';


-- =====================================================
-- 2. 报告薄弱知识点快照
-- 保存生成报告时的诊断结果，避免后续数据变化影响历史报告
-- =====================================================

CREATE TABLE IF NOT EXISTS student_learning_report_weak_point (
    id BIGINT NOT NULL AUTO_INCREMENT,

    report_id BIGINT NOT NULL COMMENT '报告ID',
    point_id BIGINT NOT NULL COMMENT '知识点ID',

    point_title VARCHAR(200) NOT NULL COMMENT '知识点名称快照',

    effective_question_count INT NOT NULL DEFAULT 0,
    question_session_count INT NOT NULL DEFAULT 0,
    question_inferred_level INT NOT NULL DEFAULT 2,

    practice_question_count INT NOT NULL DEFAULT 0,
    mastered_question_count INT NOT NULL DEFAULT 0,
    practice_mastery_rate INT DEFAULT NULL,

    diagnosis_level INT NOT NULL DEFAULT 2,
    diagnosis_label VARCHAR(50) NOT NULL,

    diagnosis_reason TEXT NOT NULL,
    suggestion TEXT NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_report_weak_point (
        report_id, point_id
    ),

    KEY idx_report_weak_point_level (
        report_id, diagnosis_level
    ),

    CONSTRAINT fk_report_weak_point_report
        FOREIGN KEY (report_id)
        REFERENCES student_learning_report(id)
        ON DELETE CASCADE

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='学习报告薄弱知识点快照';


-- =====================================================
-- 3. 教师报告批注
-- 支持同一份报告多次批注，保留批注历史
-- =====================================================

CREATE TABLE IF NOT EXISTS student_learning_report_comment (
    id BIGINT NOT NULL AUTO_INCREMENT,

    report_id BIGINT NOT NULL COMMENT '报告ID',
    teacher_id BIGINT NOT NULL COMMENT '批注教师ID',

    comment_content VARCHAR(2000) NOT NULL COMMENT '批注内容',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY idx_report_comment_report (
        report_id, created_at
    ),

    KEY idx_report_comment_teacher (
        teacher_id, created_at
    ),

    CONSTRAINT fk_report_comment_report
        FOREIGN KEY (report_id)
        REFERENCES student_learning_report(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_report_comment_teacher
        FOREIGN KEY (teacher_id)
        REFERENCES sys_user(id)
        ON DELETE RESTRICT

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='教师学习报告批注';


-- =====================================================
-- 4. 检查迁移结果
-- =====================================================

SHOW TABLES LIKE 'student_learning_report%';
