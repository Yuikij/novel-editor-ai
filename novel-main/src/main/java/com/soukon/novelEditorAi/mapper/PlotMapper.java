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
    
    /**
     * 获取当前情节的上一个情节
     * @param chapterId 章节ID
     * @param currentSortOrder 当前情节的排序
     * @return 上一个情节，如果没有则返回null
     */
    @Select("SELECT * FROM plots WHERE chapter_id = #{chapterId} AND sort_order < #{currentSortOrder} ORDER BY sort_order DESC LIMIT 1")
    Plot selectPreviousPlot(@Param("chapterId") Long chapterId, @Param("currentSortOrder") Integer currentSortOrder);
    
    /**
     * 根据章节ID和排序查询情节
     * @param chapterId 章节ID
     * @param sortOrder 排序值
     * @return 匹配的情节，如果没有则返回null
     */
    @Select("SELECT * FROM plots WHERE chapter_id = #{chapterId} AND sort_order = #{sortOrder} LIMIT 1")
    Plot selectByChapterIdAndSortOrder(@Param("chapterId") Long chapterId, @Param("sortOrder") Integer sortOrder);
    
    /**
     * 查询指定章节中所有排序大于等于指定值的情节，按排序升序
     * @param chapterId 章节ID
     * @param startSortOrder 起始排序值（包含）
     * @param excludePlotId 要排除的情节ID（可为null）
     * @return 情节列表
     */
    @Select("<script>" +
            "SELECT * FROM plots WHERE chapter_id = #{chapterId} AND sort_order >= #{startSortOrder}" +
            "<if test='excludePlotId != null'> AND id != #{excludePlotId}</if>" +
            " ORDER BY sort_order ASC" +
            "</script>")
    List<Plot> selectByChapterIdAndSortOrderGreaterEqual(@Param("chapterId") Long chapterId, 
                                                        @Param("startSortOrder") Integer startSortOrder,
                                                        @Param("excludePlotId") Long excludePlotId);
} 