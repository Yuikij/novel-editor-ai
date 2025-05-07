package com.soukon.novelEditorAi.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soukon.novelEditorAi.entities.Chapter;
import com.soukon.novelEditorAi.entities.Plot;
import com.soukon.novelEditorAi.mapper.PlotMapper;
import com.soukon.novelEditorAi.service.PlotService;
import com.soukon.novelEditorAi.service.CharacterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlotServiceImpl extends ServiceImpl<PlotMapper, Plot> implements PlotService {
    // MyBatis-Plus provides basic CRUD operations through ServiceImpl
    // You can implement custom methods here if needed
    
    @Autowired
    private CharacterService characterService;
    
    /**
     * 生成用于构建生成请求 Prompt 的单个情节信息。
     *
     * @param plot 情节实体
     * @return 包含情节描述的字符串，以列表项格式输出。
     */
    @Override
    public String toPrompt(Plot plot) {
        if (plot == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (plot.getTitle() != null && !plot.getTitle().isEmpty()) {
            sb.append("情节标题: ").append(plot.getTitle()).append("\n");
        }
        if (plot.getType() != null && !plot.getType().isEmpty()) {
            sb.append("类型: ").append(plot.getType()).append("\n");
        }
        if (plot.getDescription() != null && !plot.getDescription().isEmpty()) {
            sb.append("描述: ").append(plot.getDescription()).append("\n");
        }
        if (plot.getStatus() != null && !plot.getStatus().isEmpty()) {
            sb.append("完成情况: ").append(plot.getStatus()).append("\n");
        }
        if (plot.getCompletionPercentage() != null) {
            sb.append("完成百分比: ").append(plot.getCompletionPercentage()).append("\n");
        }
        if (plot.getWordCountGoal() != null) {
            sb.append("目标字数: ").append(plot.getWordCountGoal()).append("\n");
        }
        if (plot.getCharacterIds() != null && !plot.getCharacterIds().isEmpty()) {
            sb.append("涉及角色: ");
            for (Long cid : plot.getCharacterIds()) {
                String name = null;
                if (cid != null) {
                    com.soukon.novelEditorAi.entities.Character character = characterService.getById(cid);
                    name = (character != null && character.getName() != null) ? character.getName() : ("ID[" + cid + "]");
                }
                sb.append(name).append(", ");
            }
            // 去掉最后一个逗号和空格
            if (!sb.isEmpty() && sb.charAt(sb.length() - 2) == ',') {
                sb.delete(sb.length() - 2, sb.length());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toPrompt(Long chapterId) {
        List<Plot> plots = list(lambdaQuery().eq(Plot::getChapterId, chapterId));
        StringBuilder plotsInfo = new StringBuilder();
        if (plots != null && !plots.isEmpty()) {
            plotsInfo.append("情节列表").append(":\n");
            for (Plot plot : plots) {
                // 使用直接查库的toPrompt方法，自动获取上一章节摘要
                plotsInfo.append(toPrompt(plot));
                plotsInfo.append("-----\n");
            }
        }
        return plotsInfo.toString();
    }
} 