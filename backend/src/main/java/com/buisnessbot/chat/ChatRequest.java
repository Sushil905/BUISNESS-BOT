package com.buisnessbot.chat;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ChatRequest(
    @NotBlank(message = "sessionId is required") String sessionId,
    @NotBlank(message = "message is required") String message,
    String responseMode,
    String responseDepth,
    List<ChatAttachment> attachments) {
}
