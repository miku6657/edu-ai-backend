-- F2-02-04：切片表数据库外键约束
--
-- 执行前请先确认三个表的 ID 类型一致：
--   SHOW CREATE TABLE course_document;
--   SHOW CREATE TABLE course_chapter;
--   SHOW CREATE TABLE course_document_chunk;
--
-- 如果对应字段均为普通有符号 BIGINT，且尚未建立以下外键，可以执行。

USE edu_ai;

ALTER TABLE course_document_chunk

ADD CONSTRAINT fk_chunk_document
    FOREIGN KEY (document_id)
    REFERENCES course_document(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT,

ADD CONSTRAINT fk_chunk_chapter
    FOREIGN KEY (chapter_id)
    REFERENCES course_chapter(id)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT;
