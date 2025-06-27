package com.chat.model;

import com.chat.NewFunctions.subgroup.SubGroup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class Group implements GroupSubject {
    /**
     * 群组类，包含群组的基本信息、成员列表、观察者列表和小组列表。
     * 支持添加/删除成员、获取成员名称、小组相关操作等。
     * 使用观察者模式通知成员或其他观察者群组的变更。
     */
    private String name;
    private Set<User> members;
    private Set<GroupObserver> observers = new CopyOnWriteArraySet<>();
    private List<SubGroup> subGroups = new ArrayList<>(); // 新增：小组列表

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

    public void addMember(User user) {
        members.add(user);
        notifyObservers();
    }

    public void removeMember(User user) {
        members.remove(user);
        notifyObservers();
    }

    public Set<String> getMemberNames() {
        Set<String> memberNames = new HashSet<>();
        for (User user : members) {
            memberNames.add(user.getName());
        }
        return memberNames;
    }

    // 小组相关方法
    public List<SubGroup> getSubGroups() {
        return subGroups;
    }

    public SubGroup getSubGroupById(String id) {
        for (SubGroup sg : subGroups) {
            if (sg.getId().equals(id)) return sg;
        }
        return null;
    }

    public SubGroup getSubGroupByMember(String username) {
        for (SubGroup sg : subGroups) {
            if (sg.contains(username)) return sg;
        }
        return null;
    }

    public boolean addSubGroup(SubGroup sg) {
        // 不允许同名小组
        for (SubGroup s : subGroups) {
            if (s.getName().equals(sg.getName())) return false;
        }
        return subGroups.add(sg);
    }

    public boolean removeSubGroup(String id) {
        return subGroups.removeIf(sg -> sg.getId().equals(id));
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
