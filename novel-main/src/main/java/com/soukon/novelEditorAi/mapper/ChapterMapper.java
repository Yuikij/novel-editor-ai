package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soukon.novelEditorAi.entities.Chapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 章节Mapper
 */
@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {
    
    /**
     * 根据项目ID和排序号查询章节
     * @param projectId 项目ID
     * @param sortOrder 排序号
     * @return 章节
     */
    @Select("SELECT * FROM chapters WHERE project_id = #{projectId} AND sort_order = #{sortOrder} LIMIT 1")
    Chapter selectByProjectIdAndOrder(@Param("projectId") Long projectId, @Param("sortOrder") Integer sortOrder);
} 