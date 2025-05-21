package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Item;
import com.soukon.novelEditorAi.mapper.ItemMapper;
import com.soukon.novelEditorAi.service.ItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 条目服务实现类
 */
@Service
@Slf4j
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ItemMapper itemMapper;

    @Override
    public Result<Item> createItem(Item item) {
        try {
            // 名称不能为空
            if (!StringUtils.hasText(item.getName())) {
                return Result.error("条目名称不能为空");
            }
            
            itemMapper.insert(item);
            log.info("创建条目成功: {}", item.getId());
            return Result.success("创建成功", item);
        } catch (Exception e) {
            log.error("创建条目失败: {}", e.getMessage(), e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<List<Item>> batchCreateItems(List<Item> items) {
        try {
            if (items == null || items.isEmpty()) {
                return Result.error("条目列表不能为空");
            }
            
            List<Item> createdItems = new ArrayList<>();
            
            // 批量创建条目
            for (Item item : items) {
                // 名称不能为空
                if (!StringUtils.hasText(item.getName())) {
                    continue; // 跳过名称为空的条目
                }
                
                itemMapper.insert(item);
                createdItems.add(item);
            }
            
            if (createdItems.isEmpty()) {
                return Result.error("没有有效的条目可创建");
            }
            
            log.info("批量创建条目成功，数量: {}", createdItems.size());
            return Result.success("批量创建成功", createdItems);
        } catch (Exception e) {
            log.error("批量创建条目失败: {}", e.getMessage(), e);
            return Result.error("批量创建失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> updateItem(Item item) {
        try {
            // ID不能为空
            if (item.getId() == null) {
                return Result.error("条目ID不能为空");
            }
            
            // 检查条目是否存在
            Item existingItem = itemMapper.selectById(item.getId());
            if (existingItem == null) {
                return Result.error("条目不存在");
            }
            
            // 更新条目
            itemMapper.updateById(item);
            log.info("更新条目成功: {}", item.getId());
            return Result.success("更新成功", true);
        } catch (Exception e) {
            log.error("更新条目失败: {}", e.getMessage(), e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Boolean> deleteItem(Long id) {
        try {
            // 检查条目是否存在
            Item existingItem = itemMapper.selectById(id);
            if (existingItem == null) {
                return Result.error("条目不存在");
            }
            
            // 删除条目
            itemMapper.deleteById(id);
            log.info("删除条目成功: {}", id);
            return Result.success("删除成功", true);
        } catch (Exception e) {
            log.error("删除条目失败: {}", e.getMessage(), e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Result<Boolean> batchDeleteItems(List<Long> ids) {
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("条目ID列表不能为空");
            }
            
            // 批量删除条目
            for (Long id : ids) {
                itemMapper.deleteById(id);
            }
            
            log.info("批量删除条目成功，数量: {}", ids.size());
            return Result.success("批量删除成功", true);
        } catch (Exception e) {
            log.error("批量删除条目失败: {}", e.getMessage(), e);
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Item> getItemById(Long id) {
        try {
            Item item = itemMapper.selectById(id);
            if (item == null) {
                return Result.error("条目不存在");
            }
            
            return Result.success("查询成功", item);
        } catch (Exception e) {
            log.error("查询条目失败: {}", e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Page<Item>> pageItems(int page, int size, String name, String tag) {
        try {
            // 分页参数
            Page<Item> pageParam = new Page<>(page, size);
            
            // 构建查询条件
            LambdaQueryWrapper<Item> queryWrapper = new LambdaQueryWrapper<>();
            
            // 根据名称模糊查询
            if (StringUtils.hasText(name)) {
                queryWrapper.like(Item::getName, name);
            }
            
            // 根据标签模糊查询
            if (StringUtils.hasText(tag)) {
                queryWrapper.like(Item::getTags, tag);
            }
            
            // 执行分页查询
            Page<Item> resultPage = itemMapper.selectPage(pageParam, queryWrapper);
            
            return Result.success("查询成功", resultPage);
        } catch (Exception e) {
            log.error("分页查询条目失败: {}", e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public Result<List<Item>> getItemsByTag(String tag) {
        try {
            if (!StringUtils.hasText(tag)) {
                return Result.error("标签不能为空");
            }
            
            // 执行标签查询
            List<Item> items = itemMapper.selectByTag(tag);
            
            return Result.success("查询成功", items);
        } catch (Exception e) {
            log.error("根据标签查询条目失败: {}", e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    @Override
    public String getItemsPrompt(List<Long> ids) {
        List<Item> items = itemMapper.selectList(Wrappers.lambdaQuery(Item.class).in(Item::getId, ids));
        StringBuilder prompt = new StringBuilder();
        for (Item item : items) {
            prompt.append("条目名称: ").append(item.getName()).append("\n");
            prompt.append("标签: ").append(item.getTags()).append("\n");
            prompt.append("描述: ").append(item.getDescription()).append("\n");
            prompt.append("------------------------\n");
        }
        return prompt.toString();
    }
} 