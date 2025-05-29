package com.soukon.novelEditorAi.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.mapper.CharacterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色查询工具
 * 让LLM能够查询角色信息来辅助写作
 */
@Component
@Slf4j
public class CharacterQueryTool {
    
    private final CharacterMapper characterMapper;
    private String planId;
    private String lastQueryResult = "";
    
    public CharacterQueryTool(CharacterMapper characterMapper) {
        this.characterMapper = characterMapper;
    }
    
    @Tool(name = "character_query", description = """
            查询小说中的角色信息，用于写作时获取角色的详细设定。
            可以根据角色名称、项目ID或角色ID查询角色信息。
            返回角色的基本信息、性格特征、外貌描述、背景故事等。
            """)
    public String queryCharacter(
            Long projectId,
            String characterName,
            Long characterId,
            String queryType) {
        
        try {
            log.info("角色查询工具调用: queryType={}, projectId={}, characterName={}, characterId={}", 
                    queryType, projectId, characterName, characterId);
            
            List<Character> characters;
            
            switch (queryType) {
                case "by_name":
                    characters = queryByName(characterName, projectId);
                    break;
                    
                case "by_id":
                    Character character = characterMapper.selectById(characterId);
                    characters = character != null ? List.of(character) : List.of();
                    break;
                    
                case "by_project":
                    characters = characterMapper.selectListByProjectId(projectId);
                    break;
                    
                default:
                    return "不支持的查询类型: " + queryType;
            }
            
            String result = formatCharacterInfo(characters);
            lastQueryResult = result;
            
            log.info("角色查询结果: 找到 {} 个角色", characters.size());
            return result;
            
        } catch (Exception e) {
            log.error("角色查询工具执行失败", e);
            return "查询失败: " + e.getMessage();
        }
    }
    
    /**
     * 按名称查询角色
     */
    private List<Character> queryByName(String characterName, Long projectId) {
        if (projectId != null) {
            return characterMapper.selectListByProjectId(projectId).stream()
                    .filter(c -> c.getName().contains(characterName))
                    .collect(Collectors.toList());
        } else {
            // 如果没有指定项目ID，返回空列表
            return List.of();
        }
    }
    
    /**
     * 格式化角色信息
     */
    private String formatCharacterInfo(List<Character> characters) {
        if (characters.isEmpty()) {
            return "未找到匹配的角色信息。";
        }
        
        StringBuilder result = new StringBuilder();
        result.append("=== 角色信息查询结果 ===\n\n");
        
        for (Character character : characters) {
            result.append("**角色名称**: ").append(character.getName()).append("\n");
            
            if (character.getAge() != null) {
                result.append("**年龄**: ").append(character.getAge()).append("\n");
            }
            
            if (character.getGender() != null) {
                result.append("**性别**: ").append(character.getGender()).append("\n");
            }
            
            if (character.getRole() != null) {
                result.append("**角色类型**: ").append(character.getRole()).append("\n");
            }
            
            if (character.getDescription() != null && !character.getDescription().trim().isEmpty()) {
                result.append("**角色描述**: ").append(character.getDescription()).append("\n");
            }
            
            if (character.getPersonality() != null && !character.getPersonality().isEmpty()) {
                result.append("**性格特征**: ").append(String.join(", ", character.getPersonality())).append("\n");
            }
            
            if (character.getGoals() != null && !character.getGoals().isEmpty()) {
                result.append("**目标动机**: ").append(String.join(", ", character.getGoals())).append("\n");
            }
            
            if (character.getBackground() != null && !character.getBackground().trim().isEmpty()) {
                result.append("**背景故事**: ").append(character.getBackground()).append("\n");
            }
            
            if (character.getNotes() != null && !character.getNotes().trim().isEmpty()) {
                result.append("**补充信息**: ").append(character.getNotes()).append("\n");
            }
            
            result.append("\n---\n\n");
        }
        
        return result.toString();
    }
    
    public void setPlanId(String planId) {
        this.planId = planId;
    }
    
    public String getCurrentToolState() {
        return "最后查询结果: " + (lastQueryResult.length() > 100 ? 
                lastQueryResult.substring(0, 100) + "..." : lastQueryResult);
    }
    
    public void cleanup(String planId) {
        this.lastQueryResult = "";
    }
} 