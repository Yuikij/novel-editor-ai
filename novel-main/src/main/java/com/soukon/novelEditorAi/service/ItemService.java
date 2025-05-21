package com.soukon.novelEditorAi.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soukon.novelEditorAi.common.Result;
import com.soukon.novelEditorAi.entities.Item;

import java.util.List;

/**
 * 条目服务接口
 */
public interface ItemService {

    /**
     * 创建条目
     * @param item 条目信息
     * @return 创建结果
     */
    Result<Item> createItem(Item item);

    /**
     * 批量创建条目
     * @param items 条目列表
     * @return 创建结果
     */
    Result<List<Item>> batchCreateItems(List<Item> items);

    /**
     * 更新条目
     * @param item 条目信息
     * @return 更新结果
     */
    Result<Boolean> updateItem(Item item);

    /**
     * 删除条目
     * @param id 条目ID
     * @return 删除结果
     */
    Result<Boolean> deleteItem(Long id);

    /**
     * 批量删除条目
     * @param ids 条目ID列表
     * @return 删除结果
     */
    Result<Boolean> batchDeleteItems(List<Long> ids);

    /**
     * 根据ID获取条目
     * @param id 条目ID
     * @return 条目信息
     */
    Result<Item> getItemById(Long id);

    /**
     * 分页查询条目
     * @param page 页码
     * @param size 每页大小
     * @param name 条目名称（可选）
     * @param tag 标签（可选）
     * @return 分页结果
     */
    Result<Page<Item>> pageItems(int page, int size, String name, String tag);

    /**
     * 根据标签查询条目
     * @param tag 标签
     * @return 条目列表
     */
    Result<List<Item>> getItemsByTag(String tag);

    String getItemsPrompt(List<Long> ids);
}