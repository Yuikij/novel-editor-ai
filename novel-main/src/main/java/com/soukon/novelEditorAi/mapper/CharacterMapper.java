package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soukon.novelEditorAi.entities.Character;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色Mapper
 */
@Mapper
public interface CharacterMapper extends BaseMapper<Character> {
    
    /**
     * 根据项目ID查询角色列表
     * @param projectId 项目ID
     * @return 角色列表
     */
    @Select("SELECT * FROM characters WHERE project_id = #{projectId} ORDER BY id")
    List<Character> selectListByProjectId(@Param("projectId") Long projectId);
} 