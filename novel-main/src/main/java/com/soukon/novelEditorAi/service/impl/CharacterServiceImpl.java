package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soukon.novelEditorAi.entities.Character;
import com.soukon.novelEditorAi.entities.Project;
import com.soukon.novelEditorAi.mapper.CharacterMapper;
import com.soukon.novelEditorAi.service.CharacterService;
import com.soukon.novelEditorAi.service.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CharacterServiceImpl extends ServiceImpl<CharacterMapper, Character> implements CharacterService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed

    private final ChatClient chatClient;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CharacterServiceImpl(ChatModel openAiChatModel, ProjectService projectService) {
        this.chatClient = ChatClient.builder(openAiChatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                .defaultOptions(
                        OpenAiChatOptions.builder()
                                .temperature(0.7)
                                .build()
                )
                .build();
        this.projectService = projectService;
        this.objectMapper = new ObjectMapper();
    }

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


    public String toPrompt(Long projectId) {
        // 获取角色信息
        List<Character> characters = lambdaQuery()
                .eq(Character::getProjectId, projectId)
                .list();
        StringBuilder charactersInfo = new StringBuilder("角色信息：\n");
        if (characters != null && !characters.isEmpty()) {
            for (Character character : characters) {
                charactersInfo.append(toPrompt(character));
            }
        }
        return charactersInfo.toString();
    }
    
    /**
     * 使用LLM创建全新的角色
     *
     * @param partialCharacter 用户提供的部分角色信息，至少需要指定项目ID
     * @return 完整的角色信息
     */
    @Override
    public Character generateCharacter(Character partialCharacter) {
        if (partialCharacter == null || partialCharacter.getProjectId() == null) {
            throw new IllegalArgumentException("项目ID是必须的");
        }
        
        // 获取项目信息
        Project project = projectService.getById(partialCharacter.getProjectId());
        if (project == null) {
            throw new IllegalArgumentException("找不到指定的项目: " + partialCharacter.getProjectId());
        }
        
        // 获取项目中已有的角色列表
        List<Character> existingCharacters = this.list(
                new LambdaQueryWrapper<Character>()
                        .eq(Character::getProjectId, partialCharacter.getProjectId())
        );
        
        // 系统提示词
        String systemPrompt = """
                你是一个专业的小说角色设计助手，擅长创建丰富、有深度且合理的角色。
                
                重要说明：用户将提供小说信息和部分角色信息，你的任务是根据这些信息创建一个全新的完整角色，而不是补充已有角色信息。
                
                你需要返回一个完整的角色描述，包括以下字段的JSON格式：
                1. name: 角色名称（如果用户未提供，请创建一个符合小说风格和设定的合适名字）
                2. description: 角色简短描述（保留用户提供的描述，如果有）
                3. role: 角色类型 (主角, 配角, 反派，等等)
                4. gender: 性别
                5. age: 年龄（数字）
                6. personality: 性格特征（数组格式，例如["勇敢", "正直", "固执"]）
                7. goals: 角色目标（数组格式，例如["复仇", "保护家人"]）
                8. background: 角色背景故事
                9. notes: 补充信息或备注
                
                请确保生成的角色与小说的基本信息和类型相符，并且是一个具有深度、冲突和成长潜力的角色。
                返回的内容必须是纯JSON格式，不要包含任何其他说明文字。例如：
                
                {
                  "name": "角色名",
                  "description": "角色描述",
                  "role": "角色类型",
                  "gender": "男",
                  "age": 25,
                  "personality": ["性格特征1", "性格特征2"],
                  "goals": ["目标1", "目标2"],
                  "background": "背景故事",
                  "notes": "其他补充信息"
                }
                """;
        
        // 用户提示词
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("## 小说基本信息\n");
        userPromptBuilder.append("标题: ").append(project.getTitle()).append("\n");
        userPromptBuilder.append("类型: ").append(project.getGenre()).append("\n");
        if (project.getSynopsis() != null && !project.getSynopsis().isEmpty()) {
            userPromptBuilder.append("概要: ").append(project.getSynopsis()).append("\n");
        }
        if (project.getStyle() != null && !project.getStyle().isEmpty()) {
            userPromptBuilder.append("风格: ").append(project.getStyle()).append("\n");
        }
        if (project.getTargetAudience() != null && !project.getTargetAudience().isEmpty()) {
            userPromptBuilder.append("目标受众: ").append(project.getTargetAudience()).append("\n");
        }
        
        // 已有角色信息（仅作为参考，而不是要补充的内容）
        if (!existingCharacters.isEmpty()) {
            userPromptBuilder.append("\n## 已有角色（仅供参考，创建新角色时需避免重复或冲突）\n");
            for (Character character : existingCharacters) {
                userPromptBuilder.append(toPrompt(character));
            }
        }
        
        // 判断是否提供了角色名称
        boolean hasProvidedName = partialCharacter.getName() != null && !partialCharacter.getName().trim().isEmpty();
        
        // 部分角色信息（用户输入）- 这些是新角色的基础信息
        userPromptBuilder.append("\n## 用户提供的新角色基础信息（请基于这些信息创建完整角色）\n");
        if (hasProvidedName) {
            userPromptBuilder.append("名称: ").append(partialCharacter.getName()).append("\n");
        } else {
            userPromptBuilder.append("名称: [需要创建一个符合小说风格的角色名]\n");
        }
        if (partialCharacter.getDescription() != null && !partialCharacter.getDescription().isEmpty()) {
            userPromptBuilder.append("描述: ").append(partialCharacter.getDescription()).append("\n");
        }
        if (partialCharacter.getRole() != null && !partialCharacter.getRole().isEmpty()) {
            userPromptBuilder.append("角色类型: ").append(partialCharacter.getRole()).append("\n");
        }
        if (partialCharacter.getGender() != null && !partialCharacter.getGender().isEmpty()) {
            userPromptBuilder.append("性别: ").append(partialCharacter.getGender()).append("\n");
        }
        if (partialCharacter.getAge() != null) {
            userPromptBuilder.append("年龄: ").append(partialCharacter.getAge()).append("\n");
        }
        if (partialCharacter.getPersonality() != null && !partialCharacter.getPersonality().isEmpty()) {
            userPromptBuilder.append("性格特征: ").append(String.join(", ", partialCharacter.getPersonality())).append("\n");
        }
        if (partialCharacter.getGoals() != null && !partialCharacter.getGoals().isEmpty()) {
            userPromptBuilder.append("角色目标: ").append(String.join(", ", partialCharacter.getGoals())).append("\n");
        }
        if (partialCharacter.getBackground() != null && !partialCharacter.getBackground().isEmpty()) {
            userPromptBuilder.append("背景故事: ").append(partialCharacter.getBackground()).append("\n");
        }
        if (partialCharacter.getNotes() != null && !partialCharacter.getNotes().isEmpty()) {
            userPromptBuilder.append("补充信息: ").append(partialCharacter.getNotes()).append("\n");
        }
        
        // 根据是否提供名称添加特定指令
        if (hasProvidedName) {
            userPromptBuilder.append("\n请基于小说信息和用户提供的基础信息，创建一个完整的新角色。确保保留用户已提供的信息，并补充所有缺失的信息。返回完整的JSON格式数据。");
        } else {
            userPromptBuilder.append("\n请基于小说信息和用户提供的基础信息，创建一个完整的新角色，包括一个符合小说风格和设定的角色名称。返回完整的JSON格式数据。");
        }
        
        // 构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPromptBuilder.toString()));
        log.info("生成新角色请求: {}", messages);
        
        try {
            // 发送请求到AI服务
            Prompt prompt = new Prompt(messages);
            String response = chatClient.prompt(prompt).call().content();
            log.info("生成新角色响应: {}", response);
            
            // 解析JSON响应
            Character generatedCharacter = parseCharacterFromJson(response);
            
            // 检查生成的名称是否有效
            if (generatedCharacter.getName() == null || generatedCharacter.getName().trim().isEmpty()) {
                log.warn("AI生成的角色名为空，使用默认名称");
                generatedCharacter.setName("未命名角色");
            }
            
            // 保留原始字段，如果它们存在
            if (partialCharacter.getId() != null) {
                generatedCharacter.setId(partialCharacter.getId());
            }
            generatedCharacter.setProjectId(partialCharacter.getProjectId());
            
            // 设置创建和更新时间
            LocalDateTime now = LocalDateTime.now();
            generatedCharacter.setCreatedAt(now);
            generatedCharacter.setUpdatedAt(now);
            
            return generatedCharacter;
        } catch (Exception e) {
            log.error("生成角色失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成角色失败: " + e.getMessage());
        }
    }
    
    /**
     * 从JSON字符串解析Character对象
     */
    private Character parseCharacterFromJson(String json) {
        try {
            // 尝试直接解析
            return objectMapper.readValue(json, Character.class);
        } catch (JsonProcessingException e) {
            log.error("直接解析Character失败，尝试提取JSON部分: {}", e.getMessage());
            try {
                // 提取JSON部分
                String jsonContent = extractJsonFromString(json);
                return objectMapper.readValue(jsonContent, Character.class);
            } catch (JsonProcessingException ex) {
                log.error("解析提取后的JSON失败: {}", ex.getMessage());
                
                // 最后尝试手动解析
                try {
                    // 尝试使用Map解析
                    TypeReference<java.util.HashMap<String, Object>> typeRef = new TypeReference<>() {};
                    java.util.HashMap<String, Object> map = objectMapper.readValue(extractJsonFromString(json), typeRef);
                    
                    Character character = new Character();
                    
                    if (map.containsKey("name")) {
                        character.setName((String) map.get("name"));
                    }
                    if (map.containsKey("description")) {
                        character.setDescription((String) map.get("description"));
                    }
                    if (map.containsKey("role")) {
                        character.setRole((String) map.get("role"));
                    }
                    if (map.containsKey("gender")) {
                        character.setGender((String) map.get("gender"));
                    }
                    if (map.containsKey("age")) {
                        if (map.get("age") instanceof Integer) {
                            character.setAge((Integer) map.get("age"));
                        } else if (map.get("age") instanceof String) {
                            character.setAge(Integer.parseInt((String) map.get("age")));
                        }
                    }
                    if (map.containsKey("personality")) {
                        if (map.get("personality") instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> personality = (List<String>) map.get("personality");
                            character.setPersonality(personality);
                        } else if (map.get("personality") instanceof String) {
                            String personalityStr = (String) map.get("personality");
                            character.setPersonality(Arrays.asList(personalityStr.split(",\\s*")));
                        }
                    }
                    if (map.containsKey("goals")) {
                        if (map.get("goals") instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> goals = (List<String>) map.get("goals");
                            character.setGoals(goals);
                        } else if (map.get("goals") instanceof String) {
                            String goalsStr = (String) map.get("goals");
                            character.setGoals(Arrays.asList(goalsStr.split(",\\s*")));
                        }
                    }
                    if (map.containsKey("background")) {
                        character.setBackground((String) map.get("background"));
                    }
                    if (map.containsKey("notes")) {
                        character.setNotes((String) map.get("notes"));
                    }
                    
                    return character;
                } catch (Exception mapEx) {
                    log.error("手动解析Character失败: {}", mapEx.getMessage());
                    throw new RuntimeException("无法解析生成的角色数据: " + mapEx.getMessage());
                }
            }
        }
    }
    
    /**
     * 从字符串中提取JSON部分
     */
    private String extractJsonFromString(String input) {
        // 查找第一个{和最后一个}
        int startIdx = input.indexOf('{');
        int endIdx = input.lastIndexOf('}') + 1;
        
        if (startIdx >= 0 && endIdx > startIdx) {
            return input.substring(startIdx, endIdx);
        }
        
        // 如果找不到JSON对象标记，返回原始输入
        return input;
    }
} 