USE edu_ai;

CREATE TABLE IF NOT EXISTS course_exercise_recommendation_event (

    id BIGINT NOT NULL AUTO_INCREMENT,

    recommendation_batch_id VARCHAR(36) NOT NULL
        COMMENT '一次推荐结果的批次UUID',

    course_id BIGINT NOT NULL,

    student_id BIGINT NOT NULL,

    exercise_id BIGINT NOT NULL,

    event_type VARCHAR(20) NOT NULL
        COMMENT 'EXPOSURE曝光、CLICK点击',

    event_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),

    UNIQUE KEY uk_recommendation_event (
        recommendation_batch_id,
        student_id,
        exercise_id,
        event_type
    ),

    KEY idx_recommendation_course_time (
        course_id,
        event_at
    ),

    KEY idx_recommendation_exercise_time (
        exercise_id,
        event_at
    ),

    KEY idx_recommendation_student_time (
        student_id,
        event_at
    )

) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COMMENT='学生个性化练习推荐使用记录';
