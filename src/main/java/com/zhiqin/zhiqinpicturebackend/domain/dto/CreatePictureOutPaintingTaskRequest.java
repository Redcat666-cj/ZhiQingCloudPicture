package com.zhiqin.zhiqinpicturebackend.domain.dto;

import com.zhiqin.zhiqinpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import lombok.Data;

import java.io.Serializable;

@Data
public class CreatePictureOutPaintingTaskRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 扩图参数
     */
    private CreateOutPaintingTaskRequest.Parameters parameters;

    /**
     * 用户提示词。填写后按文修图处理，留空则按扩展比例扩图
     */
    private String userPrompt;

    private static final long serialVersionUID = 1L;
}
