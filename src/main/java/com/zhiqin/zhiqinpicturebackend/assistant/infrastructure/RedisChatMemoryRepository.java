package com.zhiqin.zhiqinpicturebackend.assistant.infrastructure;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 会话记忆的 Redis 实现。
 *
 * <p>只持久化 user / assistant / system 的纯文本轮次：工具调用的中间报文
 * （ToolResponseMessage 等）不属于对话历史，跨请求保留反而会污染上下文。
 * 每个会话一个 Redis key，value 为 JSON 数组，{@code saveAll} 为全量覆盖语义
 * （与 {@code MessageWindowChatMemory} 的窗口截断配合）。</p>
 */
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String KEY_PREFIX = "assistant:chat:memory:";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisChatMemoryRepository(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        return new ArrayList<>(stringRedisTemplate.keys(KEY_PREFIX + "*").stream()
                .map(key -> key.substring(KEY_PREFIX.length()))
                .toList());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String value = stringRedisTemplate.opsForValue().get(key(conversationId));
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        List<Message> messages = new ArrayList<>();
        for (StoredMessage stored : JSONUtil.toList(value, StoredMessage.class)) {
            switch (stored.getRole()) {
                case "user" -> messages.add(new UserMessage(stored.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(stored.getContent()));
                case "system" -> messages.add(new SystemMessage(stored.getContent()));
                default -> {
                    // 工具报文等未知类型不重建
                }
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = key(conversationId);
        if (messages == null || messages.isEmpty()) {
            stringRedisTemplate.delete(key);
            return;
        }
        List<StoredMessage> stored = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof UserMessage userMessage) {
                stored.add(StoredMessage.of("user", userMessage.getText()));
            } else if (message instanceof AssistantMessage assistantMessage) {
                stored.add(StoredMessage.of("assistant", assistantMessage.getText()));
            } else if (message instanceof SystemMessage systemMessage) {
                stored.add(StoredMessage.of("system", systemMessage.getText()));
            }
        }
        // 会话记忆与 session 同生命周期：30 天
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(stored), java.time.Duration.ofDays(30));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        stringRedisTemplate.delete(key(conversationId));
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    @Data
    private static class StoredMessage {
        private String role;
        private String content;

        private static StoredMessage of(String role, String content) {
            StoredMessage stored = new StoredMessage();
            stored.setRole(role);
            stored.setContent(content);
            return stored;
        }
    }
}
