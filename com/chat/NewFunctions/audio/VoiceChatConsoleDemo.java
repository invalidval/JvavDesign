package com.chat.NewFunctions.audio;

import java.util.Scanner;

import com.chat.NewFunctions.audio.SimpleVoiceClient;

public class VoiceChatConsoleDemo {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("请输入��户名:");
        String username = scanner.nextLine().trim();
        System.out.println("请输入密码:");
        String password = scanner.nextLine().trim();
        SimpleVoiceClient client = new SimpleVoiceClient();
        if (!client.login(username, password)) {
            System.out.println("登录失败，程序退出。");
            return;
        }
        System.out.println("登录成功！请输入对方用户名开始语音聊天:");
        String target = scanner.nextLine().trim();
        client.startVoiceChat(target);
        System.out.println("按回车键挂断...");
        System.in.read();
        client.stopVoiceChat();
        client.shutdown();
        System.out.println("已���断。");
    }
}
