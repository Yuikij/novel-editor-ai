package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.model.chapter.ChapterListDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
    
    /**
     * 分页查询章节列表（不包含content和historyContent字段）
     * @param page 分页参数
     * @param projectId 项目ID（可选）
     * @param title 章节标题（可选）
     * @param status 章节状态（可选）
     * @return 分页结果
     */
    @Select("<script>" +
            "SELECT id, project_id, template_id, title, sort_order, status, summary, notes, " +
            "word_count_goal, word_count, type, created_at, updated_at " +
            "FROM chapters " +
            "WHERE 1=1 " +
            "<if test='projectId != null'>" +
            "AND project_id = #{projectId} " +
            "</if>" +
            "<if test='title != null and title != \"\"'>" +
            "AND title LIKE CONCAT('%', #{title}, '%') " +
            "</if>" +
            "<if test='status != null and status != \"\"'>" +
            "AND status = #{status} " +
            "</if>" +
            "ORDER BY project_id ASC, sort_order ASC" +
            "</script>")
    Page<ChapterListDTO> selectPageWithoutContent(Page<ChapterListDTO> page, 
                                                  @Param("projectId") Long projectId, 
                                                  @Param("title") String title, 
                                                  @Param("status") String status);
    
    /**
     * 根据项目ID查询章节列表（不包含content和historyContent字段）
     * @param projectId 项目ID
     * @return 章节列表
     */
    @Select("SELECT id, project_id, template_id, title, sort_order, status, summary, notes, " +
            "word_count_goal, word_count, type, created_at, updated_at " +
            "FROM chapters WHERE project_id = #{projectId} ORDER BY sort_order ASC")
    List<ChapterListDTO> selectListByProjectIdWithoutContent(@Param("projectId") Long projectId);
    
    /**
     * 查询所有章节列表（不包含content和historyContent字段）
     * @return 章节列表
     */
    @Select("SELECT id, project_id, template_id, title, sort_order, status, summary, notes, " +
            "word_count_goal, word_count, type, created_at, updated_at " +
            "FROM chapters ORDER BY project_id ASC, sort_order ASC")
    List<ChapterListDTO> selectAllWithoutContent();
} 