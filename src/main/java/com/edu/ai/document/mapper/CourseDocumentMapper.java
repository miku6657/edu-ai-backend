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
     * 查询课程中已完成索引且当前生效的文档。
     *
     * 用于Embedding迁移，排除历史版本和待生效版本。
     */
    @Select("""
            SELECT *
            FROM course_document
            WHERE course_id = #{courseId}
              AND status = 'READY'
              AND version_status = 'CURRENT'
            ORDER BY id ASC
            """)
    List<CourseDocument> selectReadyByCourseId(
            @Param("courseId") Long courseId
    );

    /**
     * 查询当前课程生效文档ID。
     *
     * 用途：
     * 1. RAG检索文档白名单。
     * 2. Embedding迁移集合一致性校验。
     */
    @Select("""
            SELECT id
            FROM course_document
            WHERE course_id = #{courseId}
              AND status = 'READY'
              AND version_status = 'CURRENT'
            ORDER BY id ASC
            """)
    List<Long> selectReadyDocumentIds(
            @Param("courseId") Long courseId
    );
}
