USE edu_ai;

-- 1. 扩展知识点业务字段
ALTER TABLE course_knowledge_point

    ADD COLUMN chapter_id BIGINT NULL
        COMMENT '所属章节ID',

    ADD COLUMN parent_id BIGINT NULL
        COMMENT '父级知识点ID',

    ADD COLUMN difficulty TINYINT NOT NULL DEFAULT 2
        COMMENT '知识点难度：1基础、2中等、3进阶',

    ADD COLUMN update_time DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '最后修改时间';


-- 2. 清理已有名称首尾空格
UPDATE course_knowledge_point
SET title = TRIM(title)
WHERE title <> TRIM(title);


-- 3. 新增章节及父级范围生成列
-- MySQL唯一索引允许多个NULL，
-- 因此使用0表示未分类章节和根知识点。

ALTER TABLE course_knowledge_point

    ADD COLUMN chapter_scope BIGINT
        GENERATED ALWAYS AS (
            IFNULL(chapter_id, 0)
        ) STORED,

    ADD COLUMN parent_scope BIGINT
        GENERATED ALWAYS AS (
            IFNULL(parent_id, 0)
        ) STORED;


-- 4. 同一章节、同一父级下知识点名称唯一

CREATE UNIQUE INDEX uk_point_sibling_name
ON course_knowledge_point (
    course_id,
    chapter_scope,
    parent_scope,
    title
);


-- 5. 章节查询索引

CREATE INDEX idx_point_course_chapter
ON course_knowledge_point (
    course_id,
    chapter_id,
    sort_order
);


-- 6. 父级知识点查询索引

CREATE INDEX idx_point_course_parent
ON course_knowledge_point (
    course_id,
    parent_id
);


-- 7. 难度范围约束

ALTER TABLE course_knowledge_point
ADD CONSTRAINT chk_point_difficulty
CHECK (difficulty BETWEEN 1 AND 3);


-- 8. 查看迁移结果

DESC course_knowledge_point;

SHOW INDEX FROM course_knowledge_point;
