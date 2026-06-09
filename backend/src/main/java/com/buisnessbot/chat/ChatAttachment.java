package com.buisnessbot.chat;

public record ChatAttachment(
    String name,
    String type,
    long size,
    String category,
    String textSnippet) {
}
