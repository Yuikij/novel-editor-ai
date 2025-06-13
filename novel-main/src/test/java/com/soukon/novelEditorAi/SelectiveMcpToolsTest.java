package com.soukon.novelEditorAi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 演示如何选择性传递MCP工具给LLM的测试类
 * 
 * 展示以下几种方式：
 * 1. 传递所有MCP工具（默认方式）
 * 2. 通过prompt engineering指导工具使用
 * 3. 通过不同的ChatClient实例控制工具可见性
 * 4. 动态场景化工具使用
 */
@SpringBootTest
public class SelectiveMcpToolsTest {

    @Autowired
    private ChatModel openAiChatModel;

    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;

    private ChatClient chatClientWithTools;
    private ChatClient chatClientWithoutTools;

    @BeforeEach
    void setUp() {
        // 方式1：配置带MCP工具的ChatClient
        if (toolCallbackProvider != null) {
            chatClientWithTools = ChatClient.builder(openAiChatModel)
                    .defaultToolCallbacks(toolCallbackProvider)
                    .build();
            System.out.println("=== MCP工具已配置 ===");
            System.out.println("工具提供者类型: " + toolCallbackProvider.getClass().getSimpleName());
        } else {
            chatClientWithTools = ChatClient.builder(openAiChatModel).build();
            System.out.println("=== 警告：未找到MCP工具提供者 ===");
        }

        // 方式2：配置不带工具的ChatClient（用于对比）
        chatClientWithoutTools = ChatClient.builder(openAiChatModel).build();
    }

    @AfterEach
    void tearDown() {
        System.out.println("测试完成，清理资源");
    }

    @Test
    void testSelectiveToolUsageViaPromptEngineering() {
        System.out.println("=== 通过Prompt Engineering选择性使用工具 ===");
        
        if (toolCallbackProvider == null) {
            System.out.println("跳过测试 - 未配置MCP工具");
            return;
        }

        // 策略1：明确指定要使用的工具类型
        System.out.println("\n--- 策略1：明确指定工具类型 ---");
        String specificToolPrompt = """
            请仅使用地理编码工具查询北京天安门广场的经纬度坐标。
            不要使用天气查询工具或其他工具。
            只需要地理位置信息。
            """;
        
        testPromptStrategy("明确指定工具", specificToolPrompt);

        // 策略2：指定工具使用顺序
        System.out.println("\n--- 策略2：指定工具使用顺序 ---");
        String sequentialPrompt = """
            请按以下顺序使用工具：
            1. 首先使用地理编码工具查询北京天安门的位置
            2. 然后使用天气查询工具获取北京今天的天气
            3. 最后总结这两个信息
            """;
        
        testPromptStrategy("指定使用顺序", sequentialPrompt);

        // 策略3：条件性工具使用
        System.out.println("\n--- 策略3：条件性工具使用 ---");
        String conditionalPrompt = """
            如果你有地图相关的工具，请查询北京故宫的位置信息。
            如果你有天气相关的工具，请查询北京的天气。
            如果你没有这些工具，请告诉我你的能力限制。
            """;
        
        testPromptStrategy("条件性使用", conditionalPrompt);
    }

    @Test
    void testScenarioBasedToolUsage() {
        System.out.println("=== 基于场景的工具使用 ===");
        
        if (toolCallbackProvider == null) {
            System.out.println("跳过测试 - 未配置MCP工具");
            return;
        }

        // 场景1：小说创作助手
        System.out.println("\n--- 场景1：小说创作助手 ---");
        String novelWritingPrompt = """
            我正在写一部都市小说，故事发生在北京。
            作为小说创作助手，请帮我获取以下信息：
            
            1. 北京故宫的精确地理位置（用于场景描述）
            2. 故宫周边的著名建筑或地标（作为背景元素）
            3. 北京今天的天气情况（影响故事氛围）
            
            请提供详细、准确的信息，这将直接用于我的小说创作。
            """;
        
        testScenario("小说创作助手", novelWritingPrompt);

        // 场景2：旅行规划助手
        System.out.println("\n--- 场景2：旅行规划助手 ---");
        String travelPlanningPrompt = """
            我计划在北京旅行，请作为旅行规划助手帮我：
            
            1. 查询天安门广场的具体位置
            2. 搜索天安门附近的餐厅推荐
            3. 规划从天安门到故宫的最佳步行路线
            4. 查询今天的天气，帮我决定是否适合户外游览
            
            请提供实用的旅行建议。
            """;
        
        testScenario("旅行规划助手", travelPlanningPrompt);

        // 场景3：纯信息查询
        System.out.println("\n--- 场景3：纯信息查询 ---");
        String informationQueryPrompt = """
            请帮我查询以下信息：
            - 北京市的地理中心坐标
            - 北京今天的天气预报
            - 不需要路线规划或餐厅推荐
            
            只需要基础的地理和天气信息。
            """;
        
        testScenario("纯信息查询", informationQueryPrompt);
    }

    @Test
    void testToolAvailabilityComparison() {
        System.out.println("=== 工具可用性对比测试 ===");

        String testPrompt = """
            请告诉我你现在可以使用哪些工具？
            特别是关于地图、天气、搜索相关的工具。
            如果有这些工具，请简单演示使用一下（比如查询北京的基本信息）。
            """;

        // 测试1：带工具的ChatClient
        System.out.println("\n--- 带MCP工具的ChatClient ---");
        try {
            String responseWithTools = chatClientWithTools.prompt(testPrompt).call().content();
            System.out.println("带工具响应: " + responseWithTools.substring(0, Math.min(300, responseWithTools.length())) + "...");
        } catch (Exception e) {
            System.err.println("带工具测试失败: " + e.getMessage());
        }

        // 测试2：不带工具的ChatClient
        System.out.println("\n--- 不带工具的ChatClient ---");
        try {
            String responseWithoutTools = chatClientWithoutTools.prompt(testPrompt).call().content();
            System.out.println("不带工具响应: " + responseWithoutTools.substring(0, Math.min(300, responseWithoutTools.length())) + "...");
        } catch (Exception e) {
            System.err.println("不带工具测试失败: " + e.getMessage());
        }
    }

    @Test
    void testFunctionalToolGrouping() {
        System.out.println("=== 功能性工具分组测试 ===");
        
        if (toolCallbackProvider == null) {
            System.out.println("跳过测试 - 未配置MCP工具");
            return;
        }

        // 分组1：仅地理相关功能
        System.out.println("\n--- 分组1：仅地理相关功能 ---");
        String geoOnlyPrompt = """
            请仅使用地理相关的功能帮我：
            1. 查询上海外滩的经纬度坐标
            2. 将坐标 (121.4944, 31.2408) 转换为详细地址
            
            不要使用天气查询、路线规划或其他功能。
            """;
        
        testFunctionalGroup("地理功能组", geoOnlyPrompt);

        // 分组2：仅搜索相关功能
        System.out.println("\n--- 分组2：仅搜索相关功能 ---");
        String searchOnlyPrompt = """
            请仅使用搜索相关的功能：
            1. 在西湖附近搜索酒店
            2. 在天安门附近搜索餐厅
            
            不要进行地理编码、天气查询或路线规划。
            """;
        
        testFunctionalGroup("搜索功能组", searchOnlyPrompt);

        // 分组3：仅天气相关功能
        System.out.println("\n--- 分组3：仅天气相关功能 ---");
        String weatherOnlyPrompt = """
            请仅使用天气相关的功能：
            查询上海、广州、深圳三个城市今天的天气情况。
            
            不要进行地理查询、搜索或路线规划。
            """;
        
        testFunctionalGroup("天气功能组", weatherOnlyPrompt);
    }

    @Test
    void testToolUsageConstraints() {
        System.out.println("=== 工具使用约束测试 ===");
        
        if (toolCallbackProvider == null) {
            System.out.println("跳过测试 - 未配置MCP工具");
            return;
        }

        // 约束1：限制工具调用次数
        System.out.println("\n--- 约束1：限制工具调用次数 ---");
        String limitedCallsPrompt = """
            请在最多调用2个工具的情况下，帮我获取北京的基本信息。
            优先选择最重要的信息：位置和天气。
            """;
        
        testConstraint("限制调用次数", limitedCallsPrompt);

        // 约束2：排除特定工具
        System.out.println("\n--- 约束2：排除特定工具 ---");
        String excludeToolsPrompt = """
            请帮我查询北京的信息，但是：
            - 不要使用路线规划相关的工具
            - 不要使用导航相关的工具
            - 只使用基础查询功能
            """;
        
        testConstraint("排除特定工具", excludeToolsPrompt);

        // 约束3：优先级排序
        System.out.println("\n--- 约束3：工具优先级排序 ---");
        String priorityPrompt = """
            请按以下优先级使用工具查询北京信息：
            1. 优先级1：天气查询（最重要）
            2. 优先级2：地理位置查询
            3. 优先级3：POI搜索（如果有时间和必要）
            
            请严格按照优先级顺序执行。
            """;
        
        testConstraint("优先级排序", priorityPrompt);
    }

    /**
     * 测试提示策略
     */
    private void testPromptStrategy(String strategyName, String prompt) {
        try {
            String response = chatClientWithTools.prompt(prompt).call().content();
            System.out.println(strategyName + "响应: " + response.substring(0, Math.min(200, response.length())) + "...");
        } catch (Exception e) {
            System.err.println(strategyName + "测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试场景
     */
    private void testScenario(String scenarioName, String prompt) {
        try {
            String response = chatClientWithTools.prompt(prompt).call().content();
            System.out.println(scenarioName + "响应: " + response.substring(0, Math.min(250, response.length())) + "...");
        } catch (Exception e) {
            System.err.println(scenarioName + "测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试功能分组
     */
    private void testFunctionalGroup(String groupName, String prompt) {
        try {
            String response = chatClientWithTools.prompt(prompt).call().content();
            System.out.println(groupName + "响应: " + response.substring(0, Math.min(200, response.length())) + "...");
        } catch (Exception e) {
            System.err.println(groupName + "测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试使用约束
     */
    private void testConstraint(String constraintName, String prompt) {
        try {
            String response = chatClientWithTools.prompt(prompt).call().content();
            System.out.println(constraintName + "响应: " + response.substring(0, Math.min(200, response.length())) + "...");
        } catch (Exception e) {
            System.err.println(constraintName + "测试失败: " + e.getMessage());
        }
    }
} 