package com.appliance.repair.controller;

import com.appliance.repair.common.Result;
import com.appliance.repair.dto.ChatRequest;
import com.appliance.repair.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Test
    void testChatStream() throws Exception {
        SseEmitter emitter = new SseEmitter(60000L);
        when(chatService.chatStream(any(ChatRequest.class))).thenReturn(emitter);

        mockMvc.perform(post("/api/chat/stream")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"洗衣机不转了怎么办？\"}"))
                .andExpect(status().isOk());
    }
}
