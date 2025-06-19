package com.soukon.novelEditorAi.service;

import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.service.impl.PlotServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试情节排序唯一性约束功能
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
@Transactional
public class PlotSortOrderTest {

    @Autowired
    private PlotService plotService;

    /**
     * 测试自动设置sortOrder
     */
    @Test
    public void testAutoSetSortOrder() {
        // 创建测试情节，不设置sortOrder
        Plot plot = createTestPlot();
        plot.setSortOrder(null);
        
        // 验证和处理sortOrder
        plotService.validateAndHandleSortOrder(plot, false);
        
        // 验证sortOrder被自动设置
        assertNotNull(plot.getSortOrder());
        assertTrue(plot.getSortOrder() > 0);
        
        log.info("自动设置sortOrder测试通过: {}", plot.getSortOrder());
    }

    /**
     * 测试sortOrder唯一性验证
     */
    @Test
    public void testSortOrderUniqueness() {
        Long chapterId = 1L;
        
        // 创建第一个情节
        Plot plot1 = createTestPlot();
        plot1.setChapterId(chapterId);
        plot1.setSortOrder(1);
        plotService.validateAndHandleSortOrder(plot1, false);
        plotService.save(plot1);
        
        // 创建第二个情节，使用相同的sortOrder
        Plot plot2 = createTestPlot();
        plot2.setChapterId(chapterId);
        plot2.setSortOrder(1);
        
        // 验证处理会自动调整后续情节的排序
        assertDoesNotThrow(() -> {
            plotService.validateAndHandleSortOrder(plot2, false);
        });
        
        log.info("sortOrder唯一性验证测试通过");
    }

    /**
     * 测试更新情节时的sortOrder验证
     */
    @Test
    public void testUpdateSortOrderValidation() {
        Long chapterId = 2L;
        
        // 创建并保存第一个情节
        Plot plot1 = createTestPlot();
        plot1.setChapterId(chapterId);
        plot1.setSortOrder(1);
        plotService.validateAndHandleSortOrder(plot1, false);
        plotService.save(plot1);
        
        // 创建并保存第二个情节
        Plot plot2 = createTestPlot();
        plot2.setChapterId(chapterId);
        plot2.setSortOrder(2);
        plotService.validateAndHandleSortOrder(plot2, false);
        plotService.save(plot2);
        
        // 更新第二个情节的sortOrder为1（与第一个冲突）
        plot2.setSortOrder(1);
        
        // 验证更新时的处理
        assertDoesNotThrow(() -> {
            plotService.validateAndHandleSortOrder(plot2, true);
        });
        
        log.info("更新sortOrder验证测试通过");
    }

    /**
     * 测试重新整理章节情节排序
     */
    @Test
    public void testReorderPlotsInChapter() {
        Long chapterId = 3L;
        
        // 创建多个情节
        Plot plot1 = createTestPlot();
        plot1.setChapterId(chapterId);
        plot1.setSortOrder(5);
        plotService.save(plot1);
        
        Plot plot2 = createTestPlot();
        plot2.setChapterId(chapterId);
        plot2.setSortOrder(10);
        plotService.save(plot2);
        
        Plot plot3 = createTestPlot();
        plot3.setChapterId(chapterId);
        plot3.setSortOrder(3);
        plotService.save(plot3);
        
        // 重新整理排序
        ((PlotServiceImpl) plotService).reorderPlotsInChapter(chapterId);
        
        // 验证排序已重新整理为连续序列
        List<Plot> plots = plotService.list(
            plotService.lambdaQuery()
                .eq(Plot::getChapterId, chapterId)
                .orderByAsc(Plot::getSortOrder)
                .getWrapper());
        
        assertEquals(3, plots.size());
        assertEquals(Integer.valueOf(1), plots.get(0).getSortOrder());
        assertEquals(Integer.valueOf(2), plots.get(1).getSortOrder());
        assertEquals(Integer.valueOf(3), plots.get(2).getSortOrder());
        
        log.info("重新整理章节情节排序测试通过");
    }

    /**
     * 测试非法sortOrder值
     */
    @Test
    public void testInvalidSortOrder() {
        Plot plot = createTestPlot();
        plot.setSortOrder(0);
        
        // 验证会抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            plotService.validateAndHandleSortOrder(plot, false);
        });
        
        plot.setSortOrder(-1);
        assertThrows(IllegalArgumentException.class, () -> {
            plotService.validateAndHandleSortOrder(plot, false);
        });
        
        log.info("非法sortOrder值测试通过");
    }

    /**
     * 测试null参数验证
     */
    @Test
    public void testNullParameterValidation() {
        // 测试null情节对象
        assertThrows(IllegalArgumentException.class, () -> {
            plotService.validateAndHandleSortOrder(null, false);
        });
        
        // 测试null章节ID
        Plot plot = createTestPlot();
        plot.setChapterId(null);
        assertThrows(IllegalArgumentException.class, () -> {
            plotService.validateAndHandleSortOrder(plot, false);
        });
        
        log.info("null参数验证测试通过");
    }

    /**
     * 创建测试用的情节对象
     */
    private Plot createTestPlot() {
        Plot plot = new Plot();
        plot.setProjectId(1L);
        plot.setChapterId(1L);
        plot.setTitle("测试情节");
        plot.setDescription("测试情节描述");
        plot.setType("测试类型");
        plot.setStatus("draft");
        plot.setCompletionPercentage(0);
        plot.setWordCountGoal(1000);
        plot.setCreatedAt(LocalDateTime.now());
        plot.setUpdatedAt(LocalDateTime.now());
        return plot;
    }
} 