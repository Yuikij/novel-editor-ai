package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soukon.novelEditorAi.entities.Plot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 情节Mapper
 */
@Mapper
public interface PlotMapper extends BaseMapper<Plot> {
    
    /**
     * 根据章节ID查询关联的情节列表
     * @param chapterId 章节ID
     * @return 情节列表
     */
    @Select("SELECT * FROM plots WHERE chapter_id = #{chapterId} ORDER BY sort_order")
    List<Plot> selectListByChapterId(@Param("chapterId") Long chapterId);
} 