USE edu_ai;

CREATE TABLE IF NOT EXISTS course_document_access_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    access_type VARCHAR(20) NOT NULL
        COMMENT 'PREVIEW、DOWNLOAD',
    accessed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_material_access_course_time
        (course_id, accessed_at),
    KEY idx_material_access_document_time
        (document_id, accessed_at),
    KEY idx_material_access_student_time
        (student_id, accessed_at),
    KEY idx_material_access_course_document
        (course_id, document_id, access_type)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COMMENT='课程资料学生预览和下载记录';

SHOW CREATE TABLE course_document_access_log;
