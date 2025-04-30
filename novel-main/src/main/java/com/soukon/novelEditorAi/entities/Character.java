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
    //    角色类型
    private String role; // protagonist, antagonist, supporting
    //    性别
    private String gender;
    //    年龄
    private Integer age;
    //    性格特征
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> personality;
    //    角色目标
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> goals;
    //    角色背景
    private String background;
    //    角色补充信息
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
                sb.append("角色描述: ").append(description);
            }
            if (background != null && !background.isEmpty()) {
                sb.append(" (角色背景: ").append(background).append(")");
            }
            if (notes != null && !notes.isEmpty()) {
                sb.append(" (角色备注: ").append(notes).append(")");
            }
            if (goals != null && !goals.isEmpty()) {
                sb.append(" (角色目标: ").append(String.join(", ", goals)).append(")");
            }
            if (role != null) {
                sb.append(" (角色类型: ").append(role).append(")");
            }
            if (age != null) {
                sb.append(" (角色年龄: ").append(age).append(")");
            }
            if (gender != null) {
                sb.append(" (角色性别: ").append(gender).append(")");
            }
            if (personality != null && !personality.isEmpty()) {
                sb.append(" (角色性格: ").append(String.join(", ", personality)).append(")");
            }

            sb.append("\n");
        }
        return sb.toString();
    }
} 