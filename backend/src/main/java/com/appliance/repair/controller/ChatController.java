package com.appliance.repair.controller;

import com.appliance.repair.common.Result;
import com.appliance.repair.dto.ChatRequest;
import com.appliance.repair.entity.Conversation;
import com.appliance.repair.entity.Message;
import com.appliance.repair.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/stream")
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        return chatService.chatStream(request);
    }

    @GetMapping("/conversations")
    public Result<List<Conversation>> getConversations() {
        return Result.success(chatService.getAllConversations());
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<Message>> getMessages(@PathVariable Long id) {
        return Result.success(chatService.getConversationMessages(id));
    }
}
