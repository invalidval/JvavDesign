package com.chat.model;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class Group implements GroupSubject {
    private String name;
    private Set<User> members;
    private Set<GroupObserver> observers = new CopyOnWriteArraySet<>();

    public Group(String name) {
        this.name = name;
        this.members = new HashSet<>();
    }

    public String getName() {
        return name;
    }

    public Set<User> getMembers() {
        return members;
    }

    // 移除无效的 @Override 注解
    public void removeMember(User user) {
        members.remove(user);
        notifyObservers();
    }

    @Override
    public void addObserver(GroupObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(GroupObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers() {
        for (GroupObserver observer : observers) {
            observer.onGroupChanged(this);
        }
    }
}

// 观察者模式接口（包内可见）
interface GroupObserver {
    void onGroupChanged(Group group);
}

interface GroupSubject {
    void addObserver(GroupObserver observer);
    void removeObserver(GroupObserver observer);
    void notifyObservers();
}
