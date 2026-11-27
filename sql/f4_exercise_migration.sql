USE edu_ai;

-- =====================================================
-- F4-01：课程练习主表
-- =====================================================

CREATE TABLE IF NOT EXISTS course_exercise (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '练习ID',

    course_id BIGINT NOT NULL COMMENT '所属课程ID',

    title VARCHAR(160) NOT NULL COMMENT '练习名称',

    description VARCHAR(1000) DEFAULT NULL COMMENT '练习说明',

    difficulty TINYINT NOT NULL COMMENT '练习难度：1基础、2中等、3进阶',

    estimated_minutes INT NOT NULL COMMENT '预计用时（分钟）',

    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        COMMENT '状态：DRAFT、PUBLISHED、CLOSED',

    created_by BIGINT NOT NULL COMMENT '创建人 user_id',

    published_at DATETIME DEFAULT NULL COMMENT '发布时间',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY idx_exercise_course (course_id, status),

    KEY idx_exercise_created_by (created_by),

    KEY idx_exercise_published (status, published_at)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='课程练习';


-- =====================================================
-- F4-02：课程练习题目
-- =====================================================

CREATE TABLE IF NOT EXISTS course_exercise_question (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目ID',

    exercise_id BIGINT NOT NULL COMMENT '所属练习ID',

    knowledge_point_id BIGINT NOT NULL COMMENT '知识点ID',

    question_type VARCHAR(20) NOT NULL
        COMMENT '题型：SINGLE_CHOICE、MULTIPLE_CHOICE、TRUE_FALSE、SHORT_ANSWER',

    stem TEXT NOT NULL COMMENT '题干',

    options_json TEXT DEFAULT NULL COMMENT '选项JSON',

    correct_answer TEXT NOT NULL COMMENT '参考答案',

    explanation TEXT DEFAULT NULL COMMENT '题目解析',

    score DECIMAL(10,2) NOT NULL COMMENT '题目分值',

    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY idx_question_exercise (exercise_id, sort_order),

    KEY idx_question_knowledge_point (knowledge_point_id)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='课程练习题目';


-- =====================================================
-- F4-03：课程练习答题记录
-- =====================================================

CREATE TABLE IF NOT EXISTS course_exercise_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '答题记录ID',

    exercise_id BIGINT NOT NULL COMMENT '练习ID',

    course_id BIGINT NOT NULL COMMENT '课程ID',

    student_id BIGINT NOT NULL COMMENT '学生ID',

    attempt_no INT NOT NULL DEFAULT 1 COMMENT '第几次作答',

    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS'
        COMMENT 'IN_PROGRESS、GRADED、PENDING_REVIEW',

    total_score DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '总分',

    earned_score DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '得分',

    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',

    submitted_at DATETIME DEFAULT NULL COMMENT '提交时间',

    graded_at DATETIME DEFAULT NULL COMMENT '批改时间',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_attempt_no (exercise_id, student_id, attempt_no),

    KEY idx_attempt_student (student_id, created_at),

    KEY idx_attempt_status (exercise_id, student_id, status)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='课程练习答题记录';


-- =====================================================
-- F4-04：课程练习答题明细
-- =====================================================

CREATE TABLE IF NOT EXISTS course_exercise_attempt_answer (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '答题明细ID',

    attempt_id BIGINT NOT NULL COMMENT '答题记录ID',

    question_id BIGINT NOT NULL COMMENT '题目ID',

    student_answer TEXT DEFAULT NULL COMMENT '学生答案',

    is_correct TINYINT DEFAULT NULL
        COMMENT '是否正确：1正确、0错误、NULL待批改',

    score_earned DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '本题得分',

    pending_review TINYINT NOT NULL DEFAULT 0
        COMMENT '是否待教师批改',

    teacher_feedback VARCHAR(2000) DEFAULT NULL COMMENT '教师评语',

    graded_by BIGINT DEFAULT NULL COMMENT '批改人 user_id',

    graded_at DATETIME DEFAULT NULL COMMENT '批改时间',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_attempt_question (attempt_id, question_id),

    KEY idx_answer_attempt (attempt_id),

    KEY idx_answer_question (question_id)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='课程练习答题明细';
