package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.service.PlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/plots")
@Slf4j
public class PlotController {

    @Autowired
    private PlotService plotService;
    
    @Autowired
    private com.soukon.novelEditorAi.service.EntitySyncHelper entitySyncHelper;

    @GetMapping
    public Result<List<Plot>> list() {
        List<Plot> plots = plotService.list();
        return Result.success(plots);
    }
    
    @GetMapping("/project/{projectId}")
    public Result<List<Plot>> listByProjectId(@PathVariable("projectId") Long projectId) {
        LambdaQueryWrapper<Plot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Plot::getProjectId, projectId);
        queryWrapper.orderByAsc(Plot::getSortOrder);
        List<Plot> plots = plotService.list(queryWrapper);
        return Result.success(plots);
    }
    
    @GetMapping("/chapter/{chapterId}")
    public Result<List<Plot>> listByChapterId(@PathVariable("chapterId") Long chapterId) {
        LambdaQueryWrapper<Plot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Plot::getChapterId, chapterId);
        queryWrapper.orderByAsc(Plot::getSortOrder);
        List<Plot> plots = plotService.list(queryWrapper);
        return Result.success(plots);
    }

    @GetMapping("/page")
    public Result<Page<Plot>> page(
            @RequestParam(value = "page", defaultValue = "1", name = "page") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10", name = "pageSize") Integer pageSize,
            @RequestParam(value = "projectId", required = false, name = "projectId") Long projectId,
            @RequestParam(value = "chapterId", required = false, name = "chapterId") Long chapterId,
            @RequestParam(value = "type", required = false, name = "type") String type) {
        
        Page<Plot> pageInfo = new Page<>(page, pageSize);
        LambdaQueryWrapper<Plot> queryWrapper = new LambdaQueryWrapper<>();
        
        if (projectId != null) {
            queryWrapper.eq(Plot::getProjectId, projectId);
        }
        if (chapterId != null) {
            queryWrapper.eq(Plot::getChapterId, chapterId);
        }
        if (type != null && !type.isEmpty()) {
            queryWrapper.eq(Plot::getType, type);
        }
        
        queryWrapper.orderByAsc(Plot::getSortOrder);
        plotService.page(pageInfo, queryWrapper);
        
        return Result.success(pageInfo);
    }

    @GetMapping("/{id}")
    public Result<Plot> getById(@PathVariable("id") Long id) {
        Plot plot = plotService.getById(id);
        if (plot != null) {
            return Result.success(plot);
        }
        return Result.error("Plot not found with id: " + id);
    }

    @PostMapping
    public Result<Plot> save(@RequestBody Plot plot) {
        try {
            LocalDateTime now = LocalDateTime.now();
            plot.setCreatedAt(now);
            plot.setUpdatedAt(now);
            
            // 初始化向量版本号
            if (plot.getVectorVersion() == null) {
                plot.setVectorVersion(1L);
            }
            
            // 验证并处理sortOrder的唯一性
            plotService.validateAndHandleSortOrder(plot, false);
            
            plotService.save(plot);
            
            // 触发向量同步（异步）
            entitySyncHelper.triggerCreate("plot", plot.getId(), 
                plot.getProjectId(), false);
            
            return Result.success("Plot created successfully", plot);
        } catch (IllegalArgumentException e) {
            log.error("创建情节时参数错误: {}", e.getMessage());
            return Result.error("参数错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("创建情节时发生错误: {}", e.getMessage(), e);
            return Result.error("创建情节失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Plot> update(@PathVariable("id") Long id, @RequestBody Plot plot) {
        try {
            Plot existingPlot = plotService.getById(id);
            if (existingPlot == null) {
                return Result.error("Plot not found with id: " + id);
            }
            
            plot.setId(id);
            plot.setCreatedAt(existingPlot.getCreatedAt());
            plot.setUpdatedAt(LocalDateTime.now());
            
            // 验证并处理sortOrder的唯一性
            plotService.validateAndHandleSortOrder(plot, true);
            
            plotService.updateById(plot);
            
            return Result.success("Plot updated successfully", plot);
        } catch (IllegalArgumentException e) {
            log.error("更新情节时参数错误: {}", e.getMessage());
            return Result.error("参数错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("更新情节时发生错误: {}", e.getMessage(), e);
            return Result.error("更新情节失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Plot plot = plotService.getById(id);
        if (plot == null) {
            return Result.error("Plot not found with id: " + id);
        }
        
        plotService.removeById(id);
        return Result.success("Plot deleted successfully", null);
    }
    
    /**
     * 批量删除情节
     *
     * @param ids 情节ID列表
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("IDs list cannot be empty");
        }
        
        plotService.removeByIds(ids);
        return Result.success("批量删除成功", null);
    }
    
    /**
     * 获取章节中第一个未完成的情节
     * 
     * @param chapterId 章节ID
     * @return 第一个未完成的情节，如果所有情节都已完成或没有情节则返回null
     */
    @GetMapping("/first-incomplete/{chapterId}")
    public Result<Plot> getFirstIncompletePlot(@PathVariable("chapterId") Long chapterId) {
        if (chapterId == null) {
            return Result.error("Chapter ID is required");
        }
        
        Plot plot = plotService.getFirstIncompletePlot(chapterId);
        if (plot != null) {
            return Result.success(plot);
        } else {
            return Result.success("All plots are complete or no plots exist", null);
        }
    }
    
    /**
     * 根据章节ID和排序查找情节
     * 
     * @param chapterId 章节ID
     * @param sortOrder 排序值
     * @return 匹配的情节，如果没有则返回null
     */
    @GetMapping("/chapter/{chapterId}/sort-order/{sortOrder}")
    public Result<Plot> getByChapterIdAndSortOrder(
            @PathVariable("chapterId") Long chapterId,
            @PathVariable("sortOrder") Integer sortOrder) {
        
        if (chapterId == null) {
            return Result.error("Chapter ID is required");
        }
        if (sortOrder == null) {
            return Result.error("Sort order is required");
        }
        
        Plot plot = plotService.getByChapterIdAndSortOrder(chapterId, sortOrder);
        if (plot != null) {
            return Result.success(plot);
        } else {
            return Result.success("Plot not found with the specified chapter ID and sort order", null);
        }
    }
    
    /**
     * 自动补全或扩展情节列表到目标数量
     * 
     * @param chapterId 章节ID
     * @param targetCount 目标情节总数
     * @return 补全后的情节列表
     */
    @PostMapping("/auto-expand/{chapterId}")
    public Result<List<Plot>> autoExpandPlots(
            @PathVariable("chapterId") Long chapterId,
            @RequestParam(value = "targetCount", required = false, defaultValue = "5") Integer targetCount) {
        log.info("自动扩展情节，章节ID: {}, 目标数量: {}", chapterId, targetCount);
        try {
            // 获取章节所有现有情节ID
            LambdaQueryWrapper<Plot> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Plot::getChapterId, chapterId);
            queryWrapper.select(Plot::getId);
            List<Long> existingIds = this.plotService.list(queryWrapper)
                    .stream()
                    .map(Plot::getId)
                    .toList();
            
            // 调用扩展方法
            List<Plot> expandedPlots = plotService.expandPlots(
                    chapterId, existingIds, targetCount);
            return Result.success("情节扩展成功", expandedPlots);
        } catch (Exception e) {
            log.error("情节扩展失败: {}", e.getMessage(), e);
            return Result.error("情节扩展失败: " + e.getMessage());
        }
    }
    
    /**
     * 重新整理章节中所有情节的排序，确保连续且无重复
     * 此接口用于数据修复
     * 
     * @param chapterId 章节ID
     * @return 操作结果
     */
    @PostMapping("/reorder-chapter/{chapterId}")
    public Result<Void> reorderPlotsInChapter(@PathVariable("chapterId") Long chapterId) {
        try {
            if (chapterId == null) {
                return Result.error("章节ID不能为空");
            }
            
            // 调用PlotServiceImpl中的public方法来重新整理排序
            ((com.soukon.novelEditorAi.service.impl.PlotServiceImpl) plotService).reorderPlotsInChapter(chapterId);
            
            return Result.success("情节排序重新整理成功", null);
        } catch (IllegalArgumentException e) {
            log.error("重新整理情节排序时参数错误: {}", e.getMessage());
            return Result.error("参数错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("重新整理情节排序时发生错误: {}", e.getMessage(), e);
            return Result.error("重新整理情节排序失败: " + e.getMessage());
        }
    }
}