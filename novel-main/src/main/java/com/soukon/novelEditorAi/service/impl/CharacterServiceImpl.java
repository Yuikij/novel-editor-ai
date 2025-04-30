package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.mapper.CharacterMapper;
import com.soukon.novelEditorAi.service.CharacterService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CharacterServiceImpl extends ServiceImpl<CharacterMapper, Character> implements CharacterService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
    
    /**
     * 生成用于构建生成请求 Prompt 的单个角色信息。
     *
     * @param character 角色实体
     * @return 包含角色姓名和描述的字符串。
     */
    @Override
    public String toPrompt(Character character) {
        if (character == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        if (character.getName() != null && !character.getName().isEmpty()) {
            sb.append("- ").append(character.getName());
            if (character.getDescription() != null && !character.getDescription().isEmpty()) {
                sb.append(" (角色描述: ").append(character.getDescription()).append(")");
            }
            if (character.getBackground() != null && !character.getBackground().isEmpty()) {
                sb.append(" (角色背景: ").append(character.getBackground()).append(")");
            }
            if (character.getNotes() != null && !character.getNotes().isEmpty()) {
                sb.append(" (角色备注: ").append(character.getNotes()).append(")");
            }
            if (character.getGoals() != null && !character.getGoals().isEmpty()) {
                sb.append(" (角色目标: ").append(String.join(", ", character.getGoals())).append(")");
            }
            if (character.getRole() != null) {
                sb.append(" (角色类型: ").append(character.getRole()).append(")");
            }
            if (character.getAge() != null) {
                sb.append(" (角色年龄: ").append(character.getAge()).append(")");
            }
            if (character.getGender() != null) {
                sb.append(" (角色性别: ").append(character.getGender()).append(")");
            }
            if (character.getPersonality() != null && !character.getPersonality().isEmpty()) {
                sb.append(" (角色性格: ").append(String.join(", ", character.getPersonality())).append(")");
            }

            sb.append("\n");
        }
        return sb.toString();
    }
} 