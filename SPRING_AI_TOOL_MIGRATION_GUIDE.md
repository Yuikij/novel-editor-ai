# Spring AI 工具迁移指南

## 问题描述

在使用新版本的Spring AI时，遇到以下错误：

```
java.lang.IllegalStateException: No @Tool annotated methods found in FunctionToolCallback...
Did you mean to pass a ToolCallback or ToolCallbackProvider? If so, you have to use .toolCallbacks() instead of .tool()
```

## 根本原因

新版本的Spring AI改变了工具调用的API设计：

1. **旧版本**: 使用`FunctionToolCallback`和`WritingToolCallback`接口
2. **新版本**: 使用`@Tool`注解的方法

## 解决方案

### 1. 工具类改造

#### 旧版本写法
```java
public class CharacterQueryTool implements WritingToolCallback {
    @Override
    public WritingToolResult apply(String input, ToolContext toolContext) {
        // 处理逻辑
    }
    
    @Override
    public ToolCallback toSpringAiToolCallback() {
        return FunctionToolCallback.builder(TOOL_NAME, this)
                .description(DESCRIPTION)
                .inputSchema(PARAMETERS)
                .build();
    }
}
```

#### 新版本写法
```java
@Component
public class CharacterQueryTool {
    @Tool(name = "character_query", description = """
            查询小说中的角色信息，用于写作时获取角色的详细设定。
            可以根据角色名称、项目ID或角色ID查询角色信息。
            """)
    public String queryCharacter(
            Long projectId,
            String characterName,
            Long characterId,
            String queryType) {
        // 处理逻辑
        return result;
    }
}
```

### 2. 工具管理器改造

#### 旧版本写法
```java
public List<ToolCallback> getAllToolCallbacks(String planId) {
    List<ToolCallback> callbacks = new ArrayList<>();
    for (WritingToolCallback tool : toolInstances.values()) {
        ToolCallback callback = tool.toSpringAiToolCallback();
        callbacks.add(callback);
    }
    return callbacks;
}
```

#### 新版本写法
```java
public List<Object> getAllTools() {
    return List.of(characterQueryTool, plotQueryTool, ragSearchTool);
}
```

### 3. ChatClient使用方式

#### 旧版本写法
```java
List<ToolCallback> tools = toolManager.getAllToolCallbacks(planId);
ChatClient toolEnabledClient = chatClient.mutate()
        .defaultTools(tools.toArray(new ToolCallback[0]))
        .build();
```

#### 新版本写法
```java
List<Object> tools = toolManager.getAllTools();
ChatClient toolEnabledClient = chatClient.mutate()
        .defaultTools(tools.toArray())
        .build();
```

## 关键变化总结

### 1. 注解驱动
- 使用`@Tool`注解标记工具方法
- 方法参数直接映射为工具参数
- 返回值直接作为工具结果

### 2. 简化架构
- 不再需要实现复杂的接口
- 不需要手动构建`FunctionToolCallback`
- Spring自动处理工具注册和调用

### 3. 类型安全
- 方法参数类型安全
- 自动参数验证
- 更好的IDE支持

### 4. 依赖注入
- 工具类可以使用`@Component`注解
- 支持Spring的依赖注入
- 更好的生命周期管理

## 迁移步骤

1. **移除旧接口**: 删除`WritingToolCallback`接口实现
2. **添加注解**: 为工具类添加`@Component`和`@Tool`注解
3. **简化方法**: 将复杂的`apply`方法改为简单的业务方法
4. **更新管理器**: 修改工具管理器以返回工具实例而非回调
5. **修改调用**: 更新ChatClient的工具配置方式

## 优势

1. **代码简洁**: 减少样板代码
2. **类型安全**: 编译时类型检查
3. **易于维护**: 更清晰的代码结构
4. **更好集成**: 与Spring生态系统更好集成
5. **自动化**: Spring自动处理工具注册和参数映射

## 注意事项

1. 确保工具类使用`@Component`注解
2. 工具方法必须是public的
3. 参数类型要与JSON Schema兼容
4. 返回值建议使用String类型以便LLM理解
5. 工具描述要清晰明确，帮助LLM正确使用 