package com.zhiqin.zhiqinpicturebackend.assistant.interfaces.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 助手对话请求。
 */
@Data
public class AssistantChatRequest implements Serializable {

    /**
     * 前端生成的会话 id（同一会话多轮上下文），为空时使用 default
     */
    private String sessionId;

    /**
     * 用户消息
     */
    private String message;

    private static final long serialVersionUID = 1L;
}
