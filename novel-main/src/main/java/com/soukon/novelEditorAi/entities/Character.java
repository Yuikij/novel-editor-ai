package com.soukon.novelEditorAi.entities;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@TableName(value = "characters", autoResultMap = true)
public class Character {
    
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    
    private Long projectId;
    private String name;
//    描述
    private String description;
    private String role; // protagonist, antagonist, supporting
//    性别
    private String gender;
//    年龄
    private Integer age;
//    性格特征
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> personality;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> goals;

    private String background;
    private String notes;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<com.soukon.novelEditorAi.entities.CharacterRelationship> relationships;
    
    /**
     * 生成用于构建生成请求 Prompt 的单个角色信息。
     *
     * @return 包含角色姓名和描述的字符串。
     */
    public String toPrompt() {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isEmpty()) {
            sb.append("- ").append(name);
            if (description != null && !description.isEmpty()) {
                sb.append(": ").append(description);
            }
            // 可以根据需要添加更多信息，如 role, gender, age, personality, goals, background 等
            // 例如：
            // if (role != null) sb.append(" (角色: ").append(role).append(")");
            // if (age != null) sb.append(" (年龄: ").append(age).append(")");
            // if (personality != null && !personality.isEmpty()) {
            //     sb.append(" (性格: ").append(String.join(", ", personality)).append(")");
            // }
            sb.append("\n");
        }
        return sb.toString();
    }
} 