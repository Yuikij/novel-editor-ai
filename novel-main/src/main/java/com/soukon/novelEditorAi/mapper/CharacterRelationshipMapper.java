package com.soukon.novelEditorAi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soukon.novelEditorAi.entities.CharacterRelationship;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CharacterRelationshipMapper extends BaseMapper<CharacterRelationship> {
    // MyBatis-Plus provides basic CRUD operations
    // You can add custom methods here if needed

    /**
     * 根据项目ID查询角色关系
     */
    @Select("SELECT * FROM character_relationships WHERE project_id = #{projectId}")
    List<CharacterRelationship> selectListByProjectId(@Param("projectId") Long projectId);
} 