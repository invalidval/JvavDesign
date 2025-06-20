package com.chat.model;

public interface UserObserver {
    void onUserStatusChanged(User user);
}
