package com.edu.ai.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.edu.ai.document.entity.CourseDocument;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseDocumentMapper
        extends BaseMapper<CourseDocument> {

    /**
     * 查询已经正式发布的课程文档。
     *
     * 用于Embedding迁移。
     */
    @Select("""
            SELECT *
            FROM course_document
            WHERE course_id = #{courseId}
              AND status = 'READY'
              AND version_status = 'CURRENT'
              AND review_status = 'APPROVED'
              AND quality_status = 'NORMAL'
            ORDER BY id ASC
            """)
    List<CourseDocument> selectReadyByCourseId(
            @Param("courseId") Long courseId
    );

    /**
     * 查询当前课程允许检索的文档ID。
     *
     * 只有同时满足以下条件的文档才可参与RAG：
     * 1. 已完成解析和向量化
     * 2. 当前生效版本
     * 3. 已通过知识库发布审核
     */
    @Select("""
            SELECT id
            FROM course_document
            WHERE course_id = #{courseId}
              AND status = 'READY'
              AND version_status = 'CURRENT'
              AND review_status = 'APPROVED'
              AND quality_status = 'NORMAL'
            ORDER BY id ASC
            """)
    List<Long> selectReadyDocumentIds(
            @Param("courseId") Long courseId
    );
}
