package com.chat.model;

public class Message {
    private String sender;
    private String receiver; // 群聊时为 null
    private String content;
    private long timestamp;
    private boolean isGroup;
    private MessageType type; // 消息类型：文本、文件、图片等
    private String filePath;  // 文件路径（仅文件消息使用）
    private MessageSendStrategy sendStrategy;

    // 文本消息构造函数
    public Message(String sender, String receiver, String content, boolean isGroup) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.isGroup = isGroup;
        this.timestamp = System.currentTimeMillis();
        this.type = MessageType.TEXT;
    }

    // 文件消息构造函数
    public Message(String sender, String receiver, String filePath, boolean isGroup, MessageType type) {
        this.sender = sender;
        this.receiver = receiver;
        this.filePath = filePath;
        this.isGroup = isGroup;
        this.timestamp = System.currentTimeMillis();
        this.type = type;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public MessageType getType() {
        return type;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setSendStrategy(MessageSendStrategy strategy) {
        this.sendStrategy = strategy;
    }

    public void send() {
        if (sendStrategy != null) {
            sendStrategy.send(this);
        }
    }

    public enum MessageType {
        TEXT, FILE, IMAGE, PRIVATE // 新增私聊消息类型
    }

    // 策略模式接口
    public interface MessageSendStrategy {
        void send(Message message);
    }
}
