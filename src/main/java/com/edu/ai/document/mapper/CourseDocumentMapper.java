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
     * 查询课程中所有已经完成索引的文档。
     */
    @Select("""
            SELECT *
            FROM course_document
            WHERE course_id = #{courseId}
              AND status = 'READY'
            ORDER BY id ASC
            """)
    List<CourseDocument> selectReadyByCourseId(
            @Param("courseId") Long courseId
    );
}
