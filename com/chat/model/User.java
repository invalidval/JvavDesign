package com.chat.model;

import java.util.HashSet;
import java.util.Set;

public class User {
    private String name;
    private boolean isOnline;
    private Set<String> groups; // 用户加入的群组
    private Set<String> friends; // 好友列表

    public User(String name) {
        this.name = name;
        this.isOnline = true;
        this.groups = new HashSet<>();
        this.friends = new HashSet<>();
    }

    public String getName() {
        return name;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public void joinGroup(String groupName) {
        groups.add(groupName);
    }

    public void leaveGroup(String groupName) {
        groups.remove(groupName);
    }

    public Set<String> getFriends() {
        return friends;
    }

    public void addFriend(String friendName) {
        friends.add(friendName);
    }

    public void removeFriend(String friendName) {
        friends.remove(friendName);
    }

    public boolean isFriend(String friendName) {
        return friends.contains(friendName);
    }
}
