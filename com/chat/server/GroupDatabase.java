package com.chat.server;
import com.chat.model.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

public class GroupDatabase {
    private static Map<String, Group> groups = new ConcurrentHashMap<>();
    private static final String GROUP_FILE = "groups.xml";

    public static boolean createGroup(String groupName, Set<String> members) {
        if (groups.containsKey(groupName) || members.isEmpty()) return false;
        Group group = new Group(groupName);
        for (String username : members) {
            User user = UserDatabase.getUser(username);
            if (user != null) {
                group.addMember(user);
            }
        }
        groups.put(groupName, group);
        saveGroupsToFile();
        return true;
    }

    public static Group getGroup(String groupName) {
        return groups.get(groupName);
    }

    public static boolean addUserToGroup(String groupName, String username) {
        Group group = groups.get(groupName);
        if (group == null) return false;
        User user = UserDatabase.getUser(username);
        if (user == null || group.getMembers().contains(user)) return false;
        group.addMember(user);
        saveGroupsToFile();
        return true;
    }

    public static Set<String> getGroupsOfUser(String username) {
        User user = UserDatabase.getUser(username);
        if (user == null) return Collections.emptySet();
        Set<String> userGroups = new HashSet<>();
        for (Group group : groups.values()) {
            if (group.getMembers().contains(user)) {
                userGroups.add(group.getName());
            }
        }
        return userGroups;
    }

    public static Set<String> getGroupMembers(String groupName) {
        Group group = groups.get(groupName);
        return group != null ? group.getMemberNames() : Collections.emptySet();
    }

    public static boolean isUserInGroup(String groupName, String username) {
        Group group = groups.get(groupName);
        return group != null && group.getMembers().contains(UserDatabase.getUser(username));
    }

    public static void sendGroupMessage(String groupName, String sender, String message) {
        Server.sendGroupMessage(groupName, sender, message);
    }

    // --- 持久化相关 ---
    public static void initialize() {
        File file = new File(GROUP_FILE);
        if (!file.exists()) return;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            NodeList groupNodes = doc.getElementsByTagName("group");
            for (int i = 0; i < groupNodes.getLength(); i++) {
                Element groupElem = (Element) groupNodes.item(i);
                String groupName = groupElem.getAttribute("name");
                Group group = new Group(groupName);
                NodeList memberNodes = groupElem.getElementsByTagName("member");
                for (int j = 0; j < memberNodes.getLength(); j++) {
                    Element memberElem = (Element) memberNodes.item(j);
                    String memberName = memberElem.getTextContent();
                    User user = UserDatabase.getUser(memberName);
                    if (user != null) {
                        group.addMember(user);
                }
                }
                groups.put(groupName, group);
            }
        } catch (Exception e) {
            System.out.println("群聊数据加载失败: " + e.getMessage());
        }
    }

    private static void saveGroupsToFile() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement("groups");
            doc.appendChild(root);
            for (Group group : groups.values()) {
                Element groupElem = doc.createElement("group");
                groupElem.setAttribute("name", group.getName());
                for (User member : group.getMembers()) {
                    Element memberElem = doc.createElement("member");
                    memberElem.setTextContent(member.getName()); // 修正为 getName
                    groupElem.appendChild(memberElem);
                }
                root.appendChild(groupElem);
            }
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(new File(GROUP_FILE)));
        } catch (Exception e) {
            System.out.println("群聊数据保存失败: " + e.getMessage());
        }
    }
}
