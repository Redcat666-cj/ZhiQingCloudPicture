package com.zhiqin.zhiqinpicturebackend.assistant.interfaces;

import com.zhiqin.zhiqinpicturebackend.assistant.application.ChatAppService;
import com.zhiqin.zhiqinpicturebackend.assistant.interfaces.dto.AssistantChatRequest;
import com.zhiqin.zhiqinpicturebackend.identity.application.UserAppService;
import com.zhiqin.zhiqinpicturebackend.identity.domain.User;
import com.zhiqin.zhiqinpicturebackend.shared.domain.BusinessException;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ErrorCode;
import com.zhiqin.zhiqinpicturebackend.shared.domain.ThrowUtils;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.BaseResponse;
import com.zhiqin.zhiqinpicturebackend.shared.interfaces.ResultUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.Resource;

/**
 * AI 助手接口层。
 *
 * <p>对话走 SSE 流式（POST + SseEmitter，前端用 fetch ReadableStream 消费，
 * EventSource 不支持 POST/跨域 cookie）。事件 payload 统一为
 * {@code {type: delta|pictures|done|error, data}}。</p>
 */
@RestController
@RequestMapping("/assistant")
public class AssistantController {

    /**
     * SSE 超时：模型响应一般数秒，留足余量
     */
    private static final long SSE_TIMEOUT_MILLIS = 120_000L;

    @Resource
    private ChatAppService chatAppService;

    @Resource
    private UserAppService userAppService;

    /**
     * AI 助手对话（SSE 流式）
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody AssistantChatRequest chatRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(chatRequest == null, ErrorCode.PARAMS_ERROR);
        String message = chatRequest.getMessage();
        ThrowUtils.throwIf(message == null || message.isBlank(), ErrorCode.PARAMS_ERROR, "消息不能为空");
        ThrowUtils.throwIf(message.length() > 500, ErrorCode.PARAMS_ERROR, "消息过长");
        User loginUser = userAppService.getLoginUser(request);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        chatAppService.chat(loginUser.getId(), chatRequest.getSessionId(), message, emitter);
        return emitter;
    }

    /**
     * 清空某个会话的上下文记忆
     */
    @DeleteMapping("/conversation")
    public BaseResponse<Boolean> clearConversation(@RequestParam String sessionId, HttpServletRequest request) {
        User loginUser = userAppService.getLoginUser(request);
        chatAppService.clearConversation(loginUser.getId(), sessionId);
        return ResultUtils.success(true);
    }
}
