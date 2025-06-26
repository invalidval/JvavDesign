package com.chat.NewFunctions.subgroup;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SubGroup {
    private final String id; // 小组唯一ID
    private final String name; // 小组名称
    private final String parentGroup; // 所属群聊名
    private final Set<String> members = new HashSet<>(); // 小组成员用户名
    private final Set<String> pendingInvites = new HashSet<>(); // 新增：待接受邀请成员

    public SubGroup(String name, String parentGroup, Set<String> initialMembers) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.parentGroup = parentGroup;
        if (initialMembers != null) {
            this.members.addAll(initialMembers);
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getParentGroup() { return parentGroup; }
    public Set<String> getMembers() { return members; }
    public Set<String> getPendingInvites() { return pendingInvites; }

    public boolean addMember(String username) {
        return members.add(username);
    }
    public boolean removeMember(String username) {
        return members.remove(username);
    }
    public boolean contains(String username) {
        return members.contains(username);
    }
    public boolean inviteMember(String username) { return pendingInvites.add(username); }
    public boolean acceptInvite(String username) {
        if (pendingInvites.remove(username)) {
            return members.add(username);
        }
        return false;
    }

    public boolean isInvited(String username) { return pendingInvites.contains(username); }
}
