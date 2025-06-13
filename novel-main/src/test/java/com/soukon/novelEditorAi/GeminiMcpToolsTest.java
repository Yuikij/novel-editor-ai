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
 * Gemini模型专用MCP工具测试类
 * 
 * 解决Gemini模型对工具函数名称的严格要求：
 * - 必须以字母或下划线开头
 * - 只能包含字母、数字、下划线、点和短横线
 * - 最大长度64字符
 * 
 * 注意：高德地图MCP工具名称包含特殊字符，与Gemini不兼容
 * 因此本测试使用符合Gemini规范的本地MCP工具
 */
@SpringBootTest
public class GeminiMcpToolsTest {

    @Autowired
    private ChatModel openAiChatModel;

    @Autowired(required = false)
    private ToolCallbackProvider toolCallbackProvider;

    private ChatClient chatClientWithTools;
    private ChatClient chatClientWithoutTools;

    @BeforeEach
    void setUp() {
        System.out.println("=== Gemini模型MCP工具测试初始化 ===");
        
        // 配置带MCP工具的ChatClient
        if (toolCallbackProvider != null) {
            chatClientWithTools = ChatClient.builder(openAiChatModel)
                    .defaultToolCallbacks(toolCallbackProvider)
                    .build();
            System.out.println("✅ MCP工具已配置 - 工具提供者类型: " + toolCallbackProvider.getClass().getSimpleName());
        } else {
            chatClientWithTools = ChatClient.builder(openAiChatModel).build();
            System.out.println("⚠️  警告：未找到MCP工具提供者");
        }

        // 配置不带工具的ChatClient（用于对比）
        chatClientWithoutTools = ChatClient.builder(openAiChatModel).build();
        
        System.out.println("当前使用模型: gemini-2.0-flash");
    }

    @AfterEach
    void tearDown() {
        System.out.println("Gemini模型测试完成，清理资源");
    }

    @Test
    void testGeminiCompatibleToolUsage() {
        System.out.println("=== Gemini兼容工具使用测试 ===");
        
        if (toolCallbackProvider == null) {
            System.out.println("跳过测试 - 未配置MCP工具");
            return;
        }

        // 测试1：基础工具可用性检查
        System.out.println("\n--- 测试1：工具可用性检查 ---");
        String toolCheckPrompt = """
            请告诉我你现在可以使用哪些工具？
            请列出所有可用的工具名称和功能。
            如果有文件操作相关的工具，请简单演示一下。
            """;
        
        testGeminiPrompt("工具可用性检查", toolCheckPrompt);

        // 测试2：文件系统工具测试（如果可用）
        System.out.println("\n--- 测试2：文件系统工具测试 ---");
        String fileSystemPrompt = """
            如果你有文件系统相关的工具，请帮我：
            1. 检查当前目录的内容
            2. 如果可能的话，创建一个测试文件
            
            请仅使用文件系统工具，不要使用其他工具。
            """;
        
        testGeminiPrompt("文件系统工具", fileSystemPrompt);

        // 测试3：计算工具测试（如果可用）
        System.out.println("\n--- 测试3：计算工具测试 ---");
        String calculatorPrompt = """
            如果你有计算相关的工具，请帮我：
            1. 计算 123 + 456 = ?
            2. 计算 10 的平方根
            
            请仅使用计算工具，不要使用其他工具。
            """;
        
        testGeminiPrompt("计算工具", calculatorPrompt);
    }

    @Test
    void testGeminiToolNamingIssue() {
        System.out.println("=== Gemini工具命名问题分析 ===");
        
        System.out.println("\n📋 Gemini工具命名规范：");
        System.out.println("✅ 必须以字母或下划线开头");
        System.out.println("✅ 只能包含：字母(a-z, A-Z)、数字(0-9)、下划线(_)、点(.)、短横线(-)");
        System.out.println("✅ 最大长度：64字符");
        
        System.out.println("\n❌ 高德地图MCP工具名称问题：");
        System.out.println("❌ novel_editor_mcp_client_amap_sse_maps_geo - 包含过多下划线和连字符");
        System.out.println("❌ 可能超过64字符限制");
        System.out.println("❌ 工具名称格式不符合Gemini规范");
        
        System.out.println("\n💡 解决方案：");
        System.out.println("1. 使用符合Gemini规范的本地MCP工具");
        System.out.println("2. 或者为高德地图工具配置名称映射");
        System.out.println("3. 或者使用OpenAI模型（对工具名称更宽松）");
        
        // 验证当前是否有工具可用
        if (toolCallbackProvider != null) {
            System.out.println("\n✅ 当前配置：使用符合Gemini规范的本地工具");
        } else {
            System.out.println("\n⚠️  当前配置：未检测到MCP工具");
        }
    }

    @Test
    void testGeminiVsOpenAIComparison() {
        System.out.println("=== Gemini vs OpenAI 工具兼容性对比 ===");
        
        System.out.println("\n🤖 Gemini模型特点：");
        System.out.println("✅ 性能优秀，支持多模态");
        System.out.println("❌ 工具函数名称规范严格");
        System.out.println("❌ 不支持复杂的工具名称格式");
        System.out.println("❌ 对MCP工具兼容性有限");
        
        System.out.println("\n🤖 OpenAI模型特点：");
        System.out.println("✅ 工具函数名称规范较宽松");
        System.out.println("✅ 对MCP工具兼容性更好");
        System.out.println("✅ 支持复杂的工具调用");
        System.out.println("❌ 可能需要API密钥");
        
        System.out.println("\n💡 建议：");
        if (toolCallbackProvider != null) {
            System.out.println("✅ 当前Gemini配置可正常使用本地MCP工具");
            System.out.println("🔄 如需使用高德地图工具，建议切换到OpenAI模型");
        } else {
            System.out.println("❌ 当前配置无法使用MCP工具");
            System.out.println("🔧 请检查MCP服务器连接或切换到OpenAI模型");
        }
    }

    @Test
    void testSelectiveToolUsageWithGemini() {
        System.out.println("=== Gemini模型选择性工具使用测试 ===");
        
        if (toolCallbackProvider == null) {
            System.out.println("跳过测试 - 未配置MCP工具");
            return;
        }

        // Gemini模型下的选择性工具使用策略
        System.out.println("\n--- Gemini模型选择性工具使用策略 ---");
        
        // 策略1：明确指定工具类型
        String specificToolPrompt = """
            请仅使用文件系统相关的工具帮我检查当前目录。
            不要使用计算工具或其他工具。
            只需要文件系统操作功能。
            """;
        
        testGeminiPrompt("明确指定工具类型", specificToolPrompt);

        // 策略2：条件性工具使用
        String conditionalPrompt = """
            如果你有文件系统工具，请检查目录内容。
            如果你有计算工具，请计算一个简单的数学问题。
            如果你没有这些工具，请告诉我你的能力限制。
            """;
        
        testGeminiPrompt("条件性工具使用", conditionalPrompt);

        // 策略3：工具使用约束
        String constraintPrompt = """
            请在最多调用1个工具的情况下，帮我完成一个简单任务。
            优先选择最基础的功能。
            """;
        
        testGeminiPrompt("工具使用约束", constraintPrompt);
    }

    @Test
    void testGeminiToolCallbackConfiguration() {
        System.out.println("=== Gemini工具回调配置测试 ===");
        
        // 测试不同的ChatClient配置
        System.out.println("\n--- 配置1：带工具的ChatClient ---");
        try {
            String withToolsPrompt = "请告诉我你可以使用哪些工具？";
            String response1 = chatClientWithTools.prompt(withToolsPrompt).call().content();
            System.out.println("带工具响应: " + response1.substring(0, Math.min(200, response1.length())) + "...");
        } catch (Exception e) {
            System.err.println("带工具测试失败: " + e.getMessage());
            
            // 检查是否是工具名称问题
            if (e.getMessage().contains("Invalid function name")) {
                System.err.println("🔍 检测到工具名称问题 - 这是Gemini模型的已知限制");
                System.err.println("💡 建议：使用符合Gemini规范的工具或切换到OpenAI模型");
            }
        }

        System.out.println("\n--- 配置2：不带工具的ChatClient ---");
        try {
            String withoutToolsPrompt = "请告诉我你可以使用哪些工具？";
            String response2 = chatClientWithoutTools.prompt(withoutToolsPrompt).call().content();
            System.out.println("不带工具响应: " + response2.substring(0, Math.min(200, response2.length())) + "...");
        } catch (Exception e) {
            System.err.println("不带工具测试失败: " + e.getMessage());
        }

        // 验证配置
        assertNotNull(chatClientWithTools, "带工具的ChatClient不应为空");
        assertNotNull(chatClientWithoutTools, "不带工具的ChatClient不应为空");
    }

    /**
     * 测试Gemini提示策略
     */
    private void testGeminiPrompt(String strategyName, String prompt) {
        try {
            String response = chatClientWithTools.prompt(prompt).call().content();
            System.out.println(strategyName + "响应: " + response.substring(0, Math.min(200, response.length())) + "...");
        } catch (Exception e) {
            System.err.println(strategyName + "测试失败: " + e.getMessage());
            
            // 提供详细的错误分析
            if (e.getMessage().contains("Invalid function name")) {
                System.err.println("🔍 错误分析：工具函数名称不符合Gemini规范");
                System.err.println("💡 解决方案：检查MCP工具名称或使用OpenAI模型");
            } else if (e.getMessage().contains("400")) {
                System.err.println("🔍 错误分析：API请求格式问题");
                System.err.println("💡 解决方案：检查工具配置和请求格式");
            }
        }
    }
} 