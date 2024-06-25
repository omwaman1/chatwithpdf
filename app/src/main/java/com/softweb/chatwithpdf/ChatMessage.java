package com.softweb.chatwithpdf;

public class ChatMessage {
    private String message;
    private boolean isUserMessage;

    public ChatMessage(String message, boolean isUserMessage) {
        this.message = message;
        this.isUserMessage = isUserMessage;
    }

    public String getMessage() {
        return message;
    }

    public boolean isUserMessage() {
        return isUserMessage;
    }
}
