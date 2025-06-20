package com.chat.client;

// 观察者模式接口
public interface MessageObserver {
    void onMessageReceived(String message);
}
