USE edu_ai;

CREATE TABLE IF NOT EXISTS course_exercise_retry_list (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '重练清单ID',

    student_id BIGINT NOT NULL COMMENT '学生ID',

    title VARCHAR(100) NOT NULL COMMENT '清单名称',

    item_count INT NOT NULL DEFAULT 0 COMMENT '错题数量',

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT '状态：PENDING、COMPLETED',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY idx_retry_student (student_id, created_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='学生错题重练清单';


CREATE TABLE IF NOT EXISTS course_exercise_retry_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '清单明细ID',

    retry_list_id BIGINT NOT NULL COMMENT '重练清单ID',

    source_answer_id BIGINT NOT NULL COMMENT '来源错题答题记录ID',

    question_id BIGINT NOT NULL COMMENT '原题目ID',

    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY idx_retry_list (retry_list_id, sort_order),

    KEY idx_retry_source_answer (source_answer_id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COMMENT='学生错题重练清单明细';
