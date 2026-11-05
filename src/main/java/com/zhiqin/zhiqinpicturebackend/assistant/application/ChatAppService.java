package com.zhiqin.zhiqinpicturebackend.assistant.application;

import com.zhiqin.zhiqinpicturebackend.assistant.application.tool.PictureSearchTools;
import com.zhiqin.zhiqinpicturebackend.assistant.infrastructure.RedisChatMemoryRepository;
import com.zhiqin.zhiqinpicturebackend.assistant.domain.PictureBrief;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 助手对话应用服务：编排 会话记忆 → 模型流式调用 → 工具执行 → SSE 推送。
 *
 * <p>工具不依赖 HTTP 请求上下文：登录用户 id 经 ToolContext 注入，
 * 工具查到的图片经 pictureSink 回调实时推给前端渲染成卡片。</p>
 */
@Slf4j
@Service
public class ChatAppService {

    private static final String SYSTEM_PROMPT = """
            你是"小智"——志青旗下的云图库系统 AI 助手，帮用户在图片托管平台里找到想要的照片。
            自我介绍时说：我是志青旗下的云图库系统AI助手小智。

            硬性规则（违反即为严重错误）：
            1. 凡涉及查询/搜索/查看照片，必须调用工具获取真实数据后才能回答。
               即使你记得之前轮次的工具结果，新一轮查询也必须重新调用工具；
               严禁凭记忆或想象编造照片的名称、id、链接、标签等任何字段。
            2. 用户给出图片 id 或要求"查看某张图片"时，必须调用 getPictureDetail。
            3. 用户要求找照片时，必须调用 searchPictures。
            4. 工具返回空列表就如实说没有找到，严禁把空结果编成有结果。

            工作技巧：
            - 用户描述模糊（如"帮我找几张风景图"）时，可先用 listAccessibleSpaces 了解用户有哪些空间，
              再用 searchPictures 的 searchText / tags 参数组合搜索；
            - 用户提到"我的空间/团队空间"但没有指明是哪个时，先调用 listAccessibleSpaces 确认 spaceId；
            - 标签用词拿不准时，先调用 listSpaceTags 查看空间里实际存在的标签，再搜索。

            回答要求：使用简体中文，简洁友好，推荐照片时提及照片名称和标签；
            与找照片无关的问题，礼貌说明你的职责范围。
            """;

    /**
     * 会话记忆窗口：最近 20 条消息（10 轮对话）
     */
    private static final int MEMORY_MAX_MESSAGES = 20;

    private final ChatClient chatClient;

    private final ChatMemory chatMemory;

    public ChatAppService(OpenAiChatModel openAiChatModel,
                          RedisChatMemoryRepository redisChatMemoryRepository,
                          PictureSearchTools pictureSearchTools) {
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(MEMORY_MAX_MESSAGES)
                .build();
        this.chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(pictureSearchTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * 发起一轮对话：流式生成文本增量（delta）、工具图片（pictures），结束发 done。
     *
     * @param userId     登录用户 id（Controller 从 session 解析后传入）
     * @param sessionId  前端生成的会话 id
     * @param message    用户消息
     * @param emitter    SSE 发射器
     * @return 可取消的订阅（emitter 超时/断开时取消模型调用）
     */
    public Disposable chat(Long userId, String sessionId, String message, SseEmitter emitter) {
        String conversationId = conversationId(userId, sessionId);
        Map<String, Object> toolContext = new HashMap<>();
        toolContext.put("userId", userId);
        toolContext.put("pictureSink", (Consumer<List<PictureBrief>>) briefs ->
                send(emitter, "pictures", briefs));

        Disposable disposable = chatClient.prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .toolContext(toolContext)
                .stream()
                .content()
                .subscribe(
                        delta -> send(emitter, "delta", delta),
                        error -> {
                            log.error("AI 助手对话失败, conversationId={}", conversationId, error);
                            send(emitter, "error", "助手开小差了，请稍后重试");
                            emitter.complete();
                        },
                        () -> {
                            send(emitter, "done", "");
                            emitter.complete();
                        }
                );
        // 客户端断开时取消上游模型调用，避免白烧 token
        emitter.onTimeout(disposable::dispose);
        emitter.onError(t -> disposable.dispose());
        return disposable;
    }

    /**
     * 清空某个会话的记忆。
     */
    public void clearConversation(Long userId, String sessionId) {
        chatMemory.clear(conversationId(userId, sessionId));
    }

    private String conversationId(Long userId, String sessionId) {
        return userId + ":" + (sessionId == null || sessionId.isBlank() ? "default" : sessionId);
    }

    private void send(SseEmitter emitter, String type, Object data) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", type);
            payload.put("data", data);
            emitter.send(payload);
        } catch (Exception e) {
            log.warn("SSE 发送失败, type={}", type);
        }
    }

    /**
     * 供未来扩展：获取当前会话历史（前端"历史记录"功能可复用）。
     */
    public List<Map<String, String>> history(Long userId, String sessionId) {
        String conversationId = conversationId(userId, sessionId);
        List<Map<String, String>> result = new ArrayList<>();
        chatMemory.get(conversationId).forEach(message -> {
            Map<String, String> item = new HashMap<>();
            item.put("role", message.getMessageType().getValue());
            item.put("content", message.getText());
            result.add(item);
        });
        return result;
    }
}
