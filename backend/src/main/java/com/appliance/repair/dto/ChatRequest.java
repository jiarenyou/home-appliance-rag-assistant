package com.appliance.repair.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

    @NotBlank(message = "消息内容不能为空")
    private String message;

    private Long conversationId;

    @Builder.Default
    private int topK = 5;
}
