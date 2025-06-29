package com.chat.NewFunctions.subgroup;

import com.chat.model.Group;
import java.util.*;

public class SubGroupManager {
    // 创建小组，返回小组对象�����null（同名小组不允许）
    public static SubGroup createSubGroup(Group group, String subGroupName, Set<String> members) {
        if (group == null || subGroupName == null || subGroupName.isEmpty()) return null;
        // 检查同名
        for (SubGroup sg : group.getSubGroups()) {
            if (sg.getName().equals(subGroupName)) return null;
        }
        SubGroup sg = new SubGroup(subGroupName, group.getName(), members);
        group.addSubGroup(sg);
        com.chat.server.GroupDatabase.saveGroupsToFile(); // 新增：保存小组信息
        return sg;
    }

    // 邀请成员加入小组（返回true表示邀请成功，false表示已在其他小组或已在本小组）
    public static boolean inviteToSubGroup(Group group, String subGroupId, String username) {
        if (group == null || subGroupId == null || username == null) return false;
        // 不允许同时加入多个小组
        for (SubGroup sg : group.getSubGroups()) {
            if (sg.contains(username)) return false;
        }
        SubGroup sg = group.getSubGroupById(subGroupId);
        if (sg == null) return false;
        boolean result = sg.inviteMember(username);
        if (result) com.chat.server.GroupDatabase.saveGroupsToFile(); // 新增
        return result;
    }

    // 新增：成员主动接受邀请
    public static boolean acceptInviteToSubGroup(Group group, String subGroupId, String username) {
        if (group == null || subGroupId == null || username == null) return false;
        // 先退出原有小组
        for (SubGroup sg : group.getSubGroups()) {
            if (sg.contains(username)) {
                sg.removeMember(username);
            }
        }
        SubGroup sg = group.getSubGroupById(subGroupId);
        if (sg == null) return false;
        boolean result = sg.acceptInvite(username);
        if (result) com.chat.server.GroupDatabase.saveGroupsToFile(); // 新增
        return result;
    }

    // 成员主动加入小组（会自动退出原有小组）
    public static boolean joinSubGroup(Group group, String subGroupId, String username) {
        if (group == null || subGroupId == null || username == null) return false;
        // 先退出原有小组
        for (SubGroup sg : group.getSubGroups()) {
            if (sg.contains(username)) {
                sg.removeMember(username);
            }
        }
        SubGroup sg = group.getSubGroupById(subGroupId);
        if (sg == null) return false;
        boolean result = sg.addMember(username);
        if (result) com.chat.server.GroupDatabase.saveGroupsToFile(); // 新增
        return result;
    }

    // 成员退出小组
    public static boolean exitSubGroup(Group group, String username) {
        if (group == null || username == null) return false;
        for (SubGroup sg : group.getSubGroups()) {
            if (sg.contains(username)) {
                boolean result = sg.removeMember(username);
                if (result) {
                    com.chat.server.GroupDatabase.saveGroupsToFile(); // 新增
                    return true;
                }
            }
        }
        return false;
    }

    // 获取成员当前所在小组
    public static SubGroup getUserSubGroup(Group group, String username) {
        if (group == null || username == null) return null;
        for (SubGroup sg : group.getSubGroups()) {
            if (sg.contains(username)) return sg;
        }
        return null;
    }

    // 新增：构建小组列表字符串，格式：小组ID|小组名|成员1,成员2;小组ID2|小组名2|成员1,成员2...
    public static String buildSubGroupListString(Group group) {
        if (group == null) return "";
        StringBuilder sb = new StringBuilder();
        List<SubGroup> subGroups = group.getSubGroups();
        for (int i = 0; i < subGroups.size(); i++) {
            SubGroup sg = subGroups.get(i);
            sb.append(sg.getId()).append("|")
              .append(sg.getName()).append("|")
              .append(String.join(",", sg.getMembers()));
            if (i != subGroups.size() - 1) sb.append(";");
        }
        return sb.toString();
    }

    // 创建小组，返回null并通过errorMsg[0]返回错误信息（同名或成员冲突）
    public static SubGroup createSubGroup(Group group, String subGroupName, Set<String> members, StringBuilder errorMsg) {
        if (group == null || subGroupName == null || subGroupName.isEmpty()) {
            if (errorMsg != null) errorMsg.append("参数错误");
            return null;
        }
        // 检查同名
        for (SubGroup sg : group.getSubGroups()) {
            if (sg.getName().equals(subGroupName)) {
                if (errorMsg != null) errorMsg.append("小组名称已存在");
                return null;
            }
        }
        // 检查成员是否已在其他小组
        for (String member : members) {
            for (SubGroup sg : group.getSubGroups()) {
                if (sg.contains(member)) {
                    if (errorMsg != null) errorMsg.append("成员[" + member + "]已在小组[" + sg.getName() + "]中");
                    return null;
                }
            }
        }
        SubGroup sg = new SubGroup(subGroupName, group.getName(), members);
        group.addSubGroup(sg);
        com.chat.server.GroupDatabase.saveGroupsToFile(); // 新增：保存小组信息
        return sg;
    }
}
