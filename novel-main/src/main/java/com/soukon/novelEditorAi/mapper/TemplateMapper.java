package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soukon.novelEditorAi.entities.Template;
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
} 