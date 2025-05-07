package com.soukon.novelEditorAi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.OutlinePlotPoint;
import com.soukon.novelEditorAi.model.outline.OutlineExpansionRequest;
import com.soukon.novelEditorAi.service.OutlinePlotPointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/outline-plot-points")
public class OutlinePlotPointController {

    @Autowired
    private OutlinePlotPointService outlinePlotPointService;

    @GetMapping
    public Result<List<OutlinePlotPoint>> list() {
        List<OutlinePlotPoint> outlinePlotPoints = outlinePlotPointService.list();
        return Result.success(outlinePlotPoints);
    }
    
    @GetMapping("/project/{projectId}")
    public Result<List<OutlinePlotPoint>> listByProjectId(@PathVariable("projectId") Long projectId) {
        LambdaQueryWrapper<OutlinePlotPoint> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OutlinePlotPoint::getProjectId, projectId);
        queryWrapper.orderByAsc(OutlinePlotPoint::getSortOrder);
        List<OutlinePlotPoint> outlinePlotPoints = outlinePlotPointService.list(queryWrapper);
        return Result.success(outlinePlotPoints);
    }
    
    @GetMapping("/project/{projectId}/type/{type}")
    public Result<List<OutlinePlotPoint>> listByProjectIdAndType(
            @PathVariable("projectId") Long projectId,
            @PathVariable("type") String type) {
        LambdaQueryWrapper<OutlinePlotPoint> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OutlinePlotPoint::getProjectId, projectId)
                  .eq(OutlinePlotPoint::getType, type);
        queryWrapper.orderByAsc(OutlinePlotPoint::getSortOrder);
        List<OutlinePlotPoint> outlinePlotPoints = outlinePlotPointService.list(queryWrapper);
        return Result.success(outlinePlotPoints);
    }

    @GetMapping("/page")
    public Result<Page<OutlinePlotPoint>> page(
            @RequestParam(value = "page", defaultValue = "1", name = "page") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "10", name = "pageSize") Integer pageSize,
            @RequestParam(value = "projectId", required = false, name = "projectId") Long projectId,
            @RequestParam(value = "type", required = false, name = "type") String type) {
        
        Page<OutlinePlotPoint> pageInfo = new Page<>(page, pageSize);
        LambdaQueryWrapper<OutlinePlotPoint> queryWrapper = new LambdaQueryWrapper<>();
        
        if (projectId != null) {
            queryWrapper.eq(OutlinePlotPoint::getProjectId, projectId);
        }
        if (type != null && !type.isEmpty()) {
            queryWrapper.eq(OutlinePlotPoint::getType, type);
        }
        
        queryWrapper.orderByAsc(OutlinePlotPoint::getSortOrder);
        outlinePlotPointService.page(pageInfo, queryWrapper);
        
        return Result.success(pageInfo);
    }

    @GetMapping("/{id}")
    public Result<OutlinePlotPoint> getById(@PathVariable("id") Long id) {
        OutlinePlotPoint outlinePlotPoint = outlinePlotPointService.getById(id);
        if (outlinePlotPoint != null) {
            return Result.success(outlinePlotPoint);
        }
        return Result.error("Outline plot point not found with id: " + id);
    }

    @PostMapping
    public Result<OutlinePlotPoint> save(@RequestBody OutlinePlotPoint outlinePlotPoint) {
        LocalDateTime now = LocalDateTime.now();
        outlinePlotPoint.setCreatedAt(now);
        outlinePlotPoint.setUpdatedAt(now);
        
        // If order is not set, find the max order and set to order+1
        if (outlinePlotPoint.getSortOrder() == null) {
            LambdaQueryWrapper<OutlinePlotPoint> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(OutlinePlotPoint::getProjectId, outlinePlotPoint.getProjectId());
            if (outlinePlotPoint.getType() != null) {
                queryWrapper.eq(OutlinePlotPoint::getType, outlinePlotPoint.getType());
            }
            queryWrapper.orderByDesc(OutlinePlotPoint::getSortOrder);
            queryWrapper.last("LIMIT 1");
            
            OutlinePlotPoint lastPoint = outlinePlotPointService.getOne(queryWrapper);
            if (lastPoint != null) {
                outlinePlotPoint.setSortOrder(lastPoint.getSortOrder() + 1);
            } else {
                outlinePlotPoint.setSortOrder(1);
            }
        }
        
        outlinePlotPointService.save(outlinePlotPoint);
        return Result.success("Outline plot point created successfully", outlinePlotPoint);
    }

    @PutMapping("/{id}")
    public Result<OutlinePlotPoint> update(@PathVariable("id") Long id, @RequestBody OutlinePlotPoint outlinePlotPoint) {
        OutlinePlotPoint existingPoint = outlinePlotPointService.getById(id);
        if (existingPoint == null) {
            return Result.error("Outline plot point not found with id: " + id);
        }
        
        outlinePlotPoint.setId(id);
        outlinePlotPoint.setCreatedAt(existingPoint.getCreatedAt());
        outlinePlotPoint.setUpdatedAt(LocalDateTime.now());
        outlinePlotPointService.updateById(outlinePlotPoint);
        
        return Result.success("Outline plot point updated successfully", outlinePlotPoint);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        OutlinePlotPoint outlinePlotPoint = outlinePlotPointService.getById(id);
        if (outlinePlotPoint == null) {
            return Result.error("Outline plot point not found with id: " + id);
        }
        
        outlinePlotPointService.removeById(id);
        return Result.success("Outline plot point deleted successfully", null);
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("IDs list cannot be empty");
        }
        
        outlinePlotPointService.removeByIds(ids);
        return Result.success("Batch delete successful", null);
    }
    
    /**
     * 简化版的大纲扩展，自动获取项目现有所有情节点并扩展到目标数量
     * 
     * @param projectId 项目ID
     * @param targetCount 目标情节点总数
     * @return 补全后的情节点列表
     */
    @PostMapping("/auto-expand/{projectId}")
    public Result<List<OutlinePlotPoint>> autoExpandOutline(
            @PathVariable("projectId") Long projectId,
            @RequestParam(value = "targetCount", required = false, defaultValue = "12") Integer targetCount) {
        try {
            // 获取项目所有现有情节点ID
            LambdaQueryWrapper<OutlinePlotPoint> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(OutlinePlotPoint::getProjectId, projectId);
            queryWrapper.select(OutlinePlotPoint::getId);
            List<Long> existingIds = this.outlinePlotPointService.list(queryWrapper)
                    .stream()
                    .map(OutlinePlotPoint::getId)
                    .toList();
            
            // 调用扩展方法
            List<OutlinePlotPoint> expandedPoints = outlinePlotPointService.expandOutlinePlotPoints(
                    projectId, existingIds, targetCount);
            return Result.success("大纲扩展成功", expandedPoints);
        } catch (Exception e) {
            return Result.error("大纲扩展失败: " + e.getMessage());
        }
    }
} 