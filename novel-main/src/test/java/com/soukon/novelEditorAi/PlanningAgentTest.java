package com.soukon.novelEditorAi;

import com.soukon.novelEditorAi.agent.PlanningAgent;
import com.soukon.novelEditorAi.llm.LlmService;
import com.soukon.novelEditorAi.model.chapter.ChapterContentRequest;
import com.soukon.novelEditorAi.model.chapter.ChapterContext;
import com.soukon.novelEditorAi.model.chapter.PlanContext;
import com.soukon.novelEditorAi.model.chapter.PlanRes;
import com.soukon.novelEditorAi.model.chapter.PlanState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest
public class PlanningAgentTest {

    @Autowired
    private LlmService llmService;

    @Test
    void testPlanningAgentReActMode() {
        // 创建测试用的请求对象
        ChapterContentRequest request = new ChapterContentRequest();
        request.setChapterId(1922923956958334978L);
        request.setMaxTokens(2000);
        request.setPromptSuggestion("测试写作建议");
        
        // 创建章节上下文
        ChapterContext context = ChapterContext.builder()
                .projectId(123L)
                .novalTitle("测试小说")
                .chapterSummary("测试章节摘要")
                .build();
        request.setChapterContext(context);
        
        // 创建计划上下文
        String testPlanId = "planning-test-" + System.currentTimeMillis();
        PlanContext planContext = new PlanContext(testPlanId);
        planContext.setPlanState(PlanState.PLANNING);
        request.setPlanContext(planContext);
        
        try {
            System.out.println("=== PlanningAgent ReAct模式测试开始 ===");
            
            // 创建PlanningAgent实例
            PlanningAgent planningAgent = new PlanningAgent(llmService, request);
            planningAgent.setPlanId(testPlanId);
            
            // 准备执行参数
            Map<String, Object> planningParams = new HashMap<>();
            planningParams.put("chapterId", request.getChapterId());
            planningParams.put("projectId", context.getProjectId());
            planningParams.put("targetWordCount", request.getMaxTokens());
            planningParams.put("promptSuggestion", request.getPromptSuggestion());
            
            System.out.println("执行参数: " + planningParams);
            
            // 执行计划制定
            System.out.println("开始执行PlanningAgent...");
            planningAgent.run(planningParams);
            
            // 获取制定的计划
            PlanRes finalPlan = planningAgent.getFinalPlan();
            
            System.out.println("=== 计划制定结果 ===");
            if (finalPlan != null) {
                System.out.println("计划目标: " + finalPlan.getGoal());
                System.out.println("计划步骤数: " + (finalPlan.getPlanList() != null ? finalPlan.getPlanList().size() : 0));
                if (finalPlan.getPlanList() != null) {
                    for (int i = 0; i < finalPlan.getPlanList().size(); i++) {
                        System.out.println("步骤" + (i + 1) + ": " + finalPlan.getPlanList().get(i).getPlanContent());
                        System.out.println("目标字数: " + finalPlan.getPlanList().get(i).getGoalWordCount());
                    }
                }
                System.out.println("✅ 测试成功：PlanningAgent成功制定了写作计划");
            } else {
                System.out.println("❌ 测试失败：PlanningAgent未能制定计划");
            }
            
            System.out.println("=== 测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("❌ 测试异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 清理测试数据
            llmService.removeAgentChatClient(testPlanId);
        }
    }

    @Test
    void testPlanningAgentToolCallFlow() {
        System.out.println("=== PlanningAgent工具调用流程测试 ===");
        
        // 创建简化的测试请求
        ChapterContentRequest request = new ChapterContentRequest();
        request.setChapterId(1922923956958334978L);
        request.setMaxTokens(1000);
        request.setPromptSuggestion("请重点描写角色心理活动");
        
        // 创建章节上下文
        ChapterContext context = ChapterContext.builder()
                .projectId(456L)
                .novalTitle("测试小说标题")
                .chapterSummary("这是一个测试章节的摘要")
                .previousChapterSummary("前一章节的摘要")
                .build();
        request.setChapterContext(context);
        
        // 创建计划上下文
        String testPlanId = "tool-test-" + System.currentTimeMillis();
        PlanContext planContext = new PlanContext(testPlanId);
        planContext.setPlanState(PlanState.PLANNING);
        request.setPlanContext(planContext);
        
        try {
            System.out.println("创建PlanningAgent实例...");
            PlanningAgent planningAgent = new PlanningAgent(llmService, request);
            planningAgent.setPlanId(testPlanId);
            
            System.out.println("准备执行参数...");
            Map<String, Object> params = new HashMap<>();
            params.put("chapterId", request.getChapterId().toString());
            params.put("projectId", context.getProjectId().toString());
            params.put("targetWordCount", request.getMaxTokens());
            params.put("promptSuggestion", request.getPromptSuggestion());
            
            System.out.println("开始执行ReAct流程...");
            System.out.println("期望流程：Think -> Act(调用工具) -> Think -> Act(制定计划)");
            
            // 执行计划制定
            planningAgent.run(params);
            
            // 检查结果
            PlanRes result = planningAgent.getFinalPlan();
            if (result != null) {
                System.out.println("✅ 工具调用流程测试成功");
                System.out.println("最终计划: " + result.getGoal());
            } else {
                System.out.println("❌ 工具调用流程测试失败：未获得最终计划");
            }
            
        } catch (Exception e) {
            System.err.println("❌ 工具调用流程测试异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 清理
            llmService.removeAgentChatClient(testPlanId);
        }
    }
} 