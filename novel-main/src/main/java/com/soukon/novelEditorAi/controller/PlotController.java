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
        LocalDateTime now = LocalDateTime.now();
        plot.setCreatedAt(now);
        plot.setUpdatedAt(now);
        
        // If plotOrder is not set, find the max order and set to order+1
        if (plot.getSortOrder() == null) {
            LambdaQueryWrapper<Plot> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Plot::getProjectId, plot.getProjectId());
            if (plot.getChapterId() != null) {
                queryWrapper.eq(Plot::getChapterId, plot.getChapterId());
            }
            queryWrapper.orderByDesc(Plot::getSortOrder);
            queryWrapper.last("LIMIT 1");
            
            Plot lastPlot = plotService.getOne(queryWrapper);
            if (lastPlot != null) {
                plot.setSortOrder(lastPlot.getSortOrder() + 1);
            } else {
                plot.setSortOrder(1);
            }
        }
        
        plotService.save(plot);
        return Result.success("Plot created successfully", plot);
    }

    @PutMapping("/{id}")
    public Result<Plot> update(@PathVariable("id") Long id, @RequestBody Plot plot) {
        Plot existingPlot = plotService.getById(id);
        if (existingPlot == null) {
            return Result.error("Plot not found with id: " + id);
        }
        
        plot.setId(id);
        plot.setCreatedAt(existingPlot.getCreatedAt());
        plot.setUpdatedAt(LocalDateTime.now());
        plotService.updateById(plot);
        
        return Result.success("Plot updated successfully", plot);
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
}