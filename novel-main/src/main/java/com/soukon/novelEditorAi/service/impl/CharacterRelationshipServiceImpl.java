package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.CharacterRelationship;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.mapper.CharacterRelationshipMapper;
import com.soukon.novelEditorAi.service.CharacterRelationshipService;
import com.soukon.novelEditorAi.service.CharacterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CharacterRelationshipServiceImpl extends ServiceImpl<CharacterRelationshipMapper, CharacterRelationship> implements CharacterRelationshipService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed

    @Autowired
    private CharacterService characterService;

    /**
     * 生成用于构建生成请求 Prompt 的角色关系信息。
     *
     * @param relationship 角色关系实体
     * @return 包含角色关系描述的字符串。
     */
    @Override
    public String toPrompt(CharacterRelationship relationship) {
        if (relationship == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("- ");
        String sourceName = null;
        String targetName = null;
        if (relationship.getSourceCharacterId() != null) {
            Character source = characterService.getById(relationship.getSourceCharacterId());
            sourceName = (source != null && source.getName() != null) ? source.getName() : ("ID[" + relationship.getSourceCharacterId() + "]");
        }
        if (relationship.getTargetCharacterId() != null) {
            Character target = characterService.getById(relationship.getTargetCharacterId());
            targetName = (target != null && target.getName() != null) ? target.getName() : ("ID[" + relationship.getTargetCharacterId() + "]");
        }
        if (sourceName != null) {
            sb.append(sourceName).append(" ");
        }
        if (relationship.getRelationshipType() != null && !relationship.getRelationshipType().isEmpty()) {
            sb.append("与 ");
        }
        if (targetName != null) {
            sb.append(targetName).append(" ");
        }
        if (relationship.getRelationshipType() != null && !relationship.getRelationshipType().isEmpty()) {
            sb.append("关系: ").append(relationship.getRelationshipType());
        }
        if (relationship.getDescription() != null && !relationship.getDescription().isEmpty()) {
            sb.append(" (描述: ").append(relationship.getDescription()).append(")");
        }
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public List<CharacterRelationship> getByProjectId(Long projectId) {
        return this.baseMapper.selectListByProjectId(projectId);
    }
} 