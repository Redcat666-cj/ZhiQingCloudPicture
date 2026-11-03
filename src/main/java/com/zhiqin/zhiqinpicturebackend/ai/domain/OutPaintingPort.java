package com.zhiqin.zhiqinpicturebackend.ai.domain;

import com.zhiqin.zhiqinpicturebackend.ai.domain.dto.CreateOutPaintingTaskResponse;
import com.zhiqin.zhiqinpicturebackend.ai.domain.dto.GetOutPaintingTaskResponse;

/**
 * 扩图能力端口（防腐层）。
 *
 * <p>实现见 {@code ai.infrastructure.AliYunAiApi}（阿里云百炼万相）。
 * 应用层只依赖这个接口，换供应商时不用改业务代码。</p>
 */
public interface OutPaintingPort {

    /**
     * 创建扩图任务
     *
     * @param imageUrl   原图地址
     * @param xScale     水平扩展倍数
     * @param yScale     垂直扩展倍数
     * @param width      原图宽（可为空）
     * @param height     原图高（可为空）
     * @param userPrompt 用户提示词（可为空）
     */
    CreateOutPaintingTaskResponse createOutPaintingTask(String imageUrl, float xScale, float yScale,
                                                       Integer width, Integer height, String userPrompt);

    /**
     * 查询扩图任务结果
     */
    GetOutPaintingTaskResponse getOutPaintingTask(String taskId);
}
