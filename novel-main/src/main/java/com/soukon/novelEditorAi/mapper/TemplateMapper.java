package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.entities.Template;
import com.soukon.novelEditorAi.model.template.TemplateListDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 模板Mapper接口
 */
@Mapper
public interface TemplateMapper extends BaseMapper<Template> {
    
    /**
     * 根据标签查询模板列表
     * @param tag 标签
     * @return 模板列表
     */
    @Select("SELECT * FROM templates WHERE tags LIKE CONCAT('%', #{tag}, '%')")
    List<Template> selectByTag(@Param("tag") String tag);
    
    /**
     * 根据ID查询模板基本信息（不包含content字段）
     * @param id 模板ID
     * @return 模板基本信息
     * @deprecated 已被注解方案替代，建议使用 QueryUtils.fillSelect 配合相应的VO类：
     *             - TemplateBasicVO: 向量化进度查询
     *             - TemplateChatContextVO: 对话上下文查询  
     *             - TemplateExistenceVO: 存在性检查
     */
    @Deprecated
    @Select("SELECT id, name, tags, vector_status, vector_progress, vector_start_time, vector_end_time, vector_error_message FROM templates WHERE id = #{id}")
    Template selectByIdWithoutContent(@Param("id") Long id);
    
    /**
     * 分页查询模板列表（不包含content字段）
     * @param page 分页参数
     * @param name 模板名称（可选）
     * @param tag 标签（可选）
     * @return 分页结果
     */
    @Select("<script>" +
            "SELECT id, name, tags FROM templates " +
            "WHERE 1=1 " +
            "<if test='name != null and name != \"\"'>" +
            "AND name LIKE CONCAT('%', #{name}, '%') " +
            "</if>" +
            "<if test='tag != null and tag != \"\"'>" +
            "AND tags LIKE CONCAT('%', #{tag}, '%') " +
            "</if>" +
            "ORDER BY id DESC" +
            "</script>")
    Page<TemplateListDTO> selectPageWithoutContent(Page<TemplateListDTO> page, @Param("name") String name, @Param("tag") String tag);
    
    /**
     * 根据标签查询模板列表（不包含content字段）
     * @param tag 标签
     * @return 模板列表
     */
    @Select("SELECT id, name, tags FROM templates WHERE tags LIKE CONCAT('%', #{tag}, '%')")
    List<TemplateListDTO> selectByTagWithoutContent(@Param("tag") String tag);
} 