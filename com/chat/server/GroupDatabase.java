package com.chat.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

public class GroupDatabase {
    private static Map<String, Set<String>> groupMembers = new ConcurrentHashMap<>();
    private static Map<String, Set<String>> userGroups = new ConcurrentHashMap<>();
    private static final String GROUP_FILE = "groups.xml";

    public static boolean createGroup(String groupName, Set<String> members) {
        if (groupMembers.containsKey(groupName) || members.isEmpty()) return false;
        groupMembers.put(groupName, new HashSet<>(members));
        for (String user : members) {
            userGroups.computeIfAbsent(user, k -> new HashSet<>()).add(groupName);
        }
        saveGroupsToFile();
        return true;
    }

    public static Set<String> getGroupsOfUser(String username) {
        return userGroups.getOrDefault(username, Collections.emptySet());
    }

    public static Set<String> getGroupMembers(String groupName) {
        return groupMembers.getOrDefault(groupName, Collections.emptySet());
    }

    public static boolean isUserInGroup(String groupName, String username) {
        Set<String> members = groupMembers.get(groupName);
        return members != null && members.contains(username);
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
                Set<String> members = new HashSet<>();
                NodeList memberNodes = groupElem.getElementsByTagName("member");
                for (int j = 0; j < memberNodes.getLength(); j++) {
                    Element memberElem = (Element) memberNodes.item(j);
                    String memberName = memberElem.getTextContent();
                    members.add(memberName);
                    userGroups.computeIfAbsent(memberName, k -> new HashSet<>()).add(groupName);
                }
                groupMembers.put(groupName, members);
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
            for (String groupName : groupMembers.keySet()) {
                Element groupElem = doc.createElement("group");
                groupElem.setAttribute("name", groupName);
                for (String member : groupMembers.get(groupName)) {
                    Element memberElem = doc.createElement("member");
                    memberElem.setTextContent(member);
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
