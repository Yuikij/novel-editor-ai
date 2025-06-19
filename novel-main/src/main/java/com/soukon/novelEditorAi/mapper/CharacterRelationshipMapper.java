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
    
    /**
     * 根据角色ID列表查询相关的角色关系
     * @param characterIds 角色ID列表
     * @return 角色关系列表
     */
    @Select("<script>" +
            "SELECT * FROM character_relationships WHERE " +
            "source_character_id IN " +
            "<foreach collection='characterIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " OR target_character_id IN " +
            "<foreach collection='characterIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<CharacterRelationship> selectListByCharacterIds(@Param("characterIds") List<Long> characterIds);
} 