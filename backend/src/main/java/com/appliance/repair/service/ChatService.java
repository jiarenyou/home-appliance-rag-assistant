package com.appliance.repair.service;

import com.appliance.repair.dto.ChatRequest;
import com.appliance.repair.entity.Conversation;
import com.appliance.repair.entity.Message;
import com.appliance.repair.entity.MessageRole;
import com.appliance.repair.repository.ConversationRepository;
import com.appliance.repair.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final VectorStoreService vectorStoreService;
    private final OpenAiChatModel chatModel;

    private final ChatMemory chatMemory = new InMemoryChatMemory();

    public SseEmitter chatStream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(60000L);

        // 在新线程中处理
        new Thread(() -> {
            try {
                handleChatRequest(request, emitter);
            } catch (Exception e) {
                log.error("Chat error", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage()));
                    emitter.complete();
                } catch (IOException ioException) {
                    log.error("Failed to send error", ioException);
                }
            }
        }).start();

        return emitter;
    }

    private void handleChatRequest(ChatRequest request, SseEmitter emitter) throws IOException {
        // 获取或创建会话
        Conversation conversation = getOrCreateConversation(request.getConversationId());

        // 构建提示词
        String systemPrompt = buildSystemPrompt(request.getMessage());

        // 创建 ChatClient
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new PromptChatMemoryAdvisor(chatMemory)
                )
                .build();

        // 保存用户消息
        Message userMessage = Message.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(request.getMessage())
                .build();
        messageRepository.save(userMessage);

        // 发送用户消息确认
        emitter.send(SseEmitter.event()
                .name("user-message")
                .data(Map.of("id", userMessage.getId(), "content", request.getMessage())));

        // 流式生成回复
        StringBuilder fullResponse = new StringBuilder();

        chatClient.prompt()
                .user(request.getMessage())
                .system(systemPrompt)
                .stream()
                .content()
                .subscribe(
                        content -> {
                            fullResponse.append(content);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("content")
                                        .data(content));
                            } catch (IOException e) {
                                log.error("Failed to send content", e);
                            }
                        },
                        error -> {
                            log.error("Stream error", error);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(error.getMessage()));
                            } catch (IOException e) {
                                log.error("Failed to send error", e);
                            }
                            emitter.complete();
                        },
                        () -> {
                            // 保存助手消息
                            Message assistantMessage = Message.builder()
                                    .conversation(conversation)
                                    .role(MessageRole.ASSISTANT)
                                    .content(fullResponse.toString())
                                    .build();
                            messageRepository.save(assistantMessage);

                            // 发送完成事件
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("done")
                                        .data(Map.of(
                                                "conversationId", conversation.getId(),
                                                "messageId", assistantMessage.getId()
                                        )));
                            } catch (IOException e) {
                                log.error("Failed to send done event", e);
                            }
                            emitter.complete();
                        }
                );

        // 更新会话时间
        conversationRepository.save(conversation);
    }

    private Conversation getOrCreateConversation(Long conversationId) {
        if (conversationId != null) {
            return conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new com.appliance.repair.exception.BusinessException(
                            404, "会话不存在"));
        }

        // 创建新会话
        Conversation conversation = new Conversation();
        conversation.setTitle("新对话");
        return conversationRepository.save(conversation);
    }

    private String buildSystemPrompt(String userMessage) {
        // 检索相关文档
        var relevantDocs = vectorStoreService.searchSimilar(userMessage, 5);

        if (relevantDocs.isEmpty()) {
            return """
                    你是一位专业的家电维修技术专家助手。

                    ## 注意
                    当前知识库中没有找到与用户问题直接相关的维修手册内容。
                    请根据你的一般知识提供建议，但务必告知用户：
                    1. 这些建议仅供参考，具体操作请参考官方维修手册
                    2. 涉及安全问题时，建议联系专业维修人员

                    请专业、准确、友好地回答用户问题。
                    """;
        }

        String context = relevantDocs.stream()
                .map(doc -> doc.getContent())
                .collect(Collectors.joining("\n\n---\n\n"));

        return """
                你是一位专业的家电维修技术专家助手。

                ## 参考知识
                以下是从维修手册中检索到的相关内容：

                %s

                ## 回答要求
                1. 基于上述参考知识回答用户问题
                2. 提供清晰、详细的维修步骤
                3. 如涉及安全注意事项，请务必提醒用户
                4. 回答应专业、准确、易于理解
                5. 使用 Markdown 格式组织内容

                现在请回答用户的问题。
                """.formatted(context);
    }

    public List<Message> getConversationMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    public List<Conversation> getAllConversations() {
        return conversationRepository.findAll();
    }
}
