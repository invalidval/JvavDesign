package com.chat.NewFunctions.audio;

import com.chat.client.Client;
import java.io.*;

public class SimpleVoiceClient {
    private Client client;
    private VoiceChatManager voiceManager;
    private String username;
    private String serverHost = "127.0.0.1";
    private int serverPort = 12345; // 主消息端口
    private int voicePort = 20001;  // 语音socket端口
    private String sessionId;

    public SimpleVoiceClient() {
        voiceManager = new VoiceChatManager();
    }

    public boolean login(String username, String password) {
        try {
            client = new Client(serverHost, serverPort);
            client.sendMessage("/l " + username + " " + password);
            String resp = client.receiveMessage();
            if (resp != null && resp.startsWith("SUCCESS")) {
                this.username = username;
                client.startListening();
                return true;
            }
        } catch (Exception e) {
            System.out.println("登录异常: " + e.getMessage());
        }
        return false;
    }

    public void startVoiceChat(String targetUser) {
        try {
            // 1. 连接语音socket
            voiceManager.connectToVoiceServer(serverHost, voicePort);
            // 2. 注册sessionId
            sessionId = username + "_to_" + targetUser;
            // 3. 向服务器注册语音socket身份
            ObjectOutputStream out = voiceManager.getVoiceOut();
            out.writeObject(sessionId);
            out.flush();
            // 4. 启动语音会话
            voiceManager.startSession(sessionId);
        } catch (Exception e) {
            System.out.println("语音通话启动失败: " + e.getMessage());
        }
    }

    public void stopVoiceChat() {
        if (sessionId != null) {
            voiceManager.stopSession(sessionId);
        }
    }

    public void shutdown() {
        if (client != null) client.stopListening();
        voiceManager.shutdown();
    }
}
