package com.chat.server;

import com.chat.model.Message;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息存储管理类 - 基于XML文件存储
 * 负责消息的持久化存储和历史查询
 */
public class MessageStorage {
    private static final String MESSAGE_FILE = "messages.xml";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static AtomicLong messageIdCounter = new AtomicLong(1);

    // 内存缓存，提高查询性能
    private static Map<String, List<StoredMessage>> userMessageCache = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    /**
     * 初始化消息存储系统
     */
    public static void initialize() {
        if (initialized)
            return;

        loadMessagesFromFile();
        initialized = true;
        System.out.println("消息存储系统已初始化");
    }

    /**
     * 保存消息到存储
     */
    public static void saveMessage(String sender, String receiver, String content, MessageType type) {
        if (!initialized)
            initialize();

        StoredMessage message = new StoredMessage(
                messageIdCounter.getAndIncrement(),
                sender,
                receiver,
                content,
                type,
                LocalDateTime.now());

        // 添加到缓存
        addToCache(message);

        // 保存到文件
        saveToFile(message);

        System.out.println("消息已保存: " + sender + " -> " + (receiver != null ? receiver : "群聊") + ": " + content);
    }

    /**
     * 获取用户的历史消息
     */
    public static List<StoredMessage> getUserMessages(String username, int limit) {
        if (!initialized)
            initialize();

        List<StoredMessage> userMessages = userMessageCache.getOrDefault(username, new ArrayList<>());

        // 按时间倒序排列，返回最新的消息
        userMessages.sort((m1, m2) -> m2.getTimestamp().compareTo(m1.getTimestamp()));

        if (limit > 0 && userMessages.size() > limit) {
            return userMessages.subList(0, limit);
        }

        return userMessages;
    }

    /**
     * 获取两个用户之间的私聊历史
     */
    public static List<StoredMessage> getPrivateMessages(String user1, String user2, int limit) {
        if (!initialized)
            initialize();

        List<StoredMessage> privateMessages = new ArrayList<>();

        // 从两个用户的缓存中查找相互的私聊消息
        List<StoredMessage> user1Messages = userMessageCache.getOrDefault(user1, new ArrayList<>());
        List<StoredMessage> user2Messages = userMessageCache.getOrDefault(user2, new ArrayList<>());

        for (StoredMessage msg : user1Messages) {
            if (msg.getType() == MessageType.PRIVATE &&
                    ((msg.getSender().equals(user1) && user2.equals(msg.getReceiver())) ||
                            (msg.getSender().equals(user2) && user1.equals(msg.getReceiver())))) {
                privateMessages.add(msg);
            }
        }

        for (StoredMessage msg : user2Messages) {
            if (msg.getType() == MessageType.PRIVATE &&
                    ((msg.getSender().equals(user2) && user1.equals(msg.getReceiver())) ||
                            (msg.getSender().equals(user1) && user2.equals(msg.getReceiver())))) {
                if (!privateMessages.contains(msg)) {
                    privateMessages.add(msg);
                }
            }
        }

        // 按时间排序
        privateMessages.sort(Comparator.comparing(StoredMessage::getTimestamp));

        if (limit > 0 && privateMessages.size() > limit) {
            return privateMessages.subList(Math.max(0, privateMessages.size() - limit), privateMessages.size());
        }

        return privateMessages;
    }

    /**
     * 获取指定群聊的历史消息
     */
    public static List<StoredMessage> getGroupMessages(String groupName, int limit) {
        if (!initialized)
            initialize();

        List<StoredMessage> groupMessages = new ArrayList<>();
        for (List<StoredMessage> msgs : userMessageCache.values()) {
            for (StoredMessage msg : msgs) {
                if (msg.getType() == MessageType.GROUP && groupName.equals(msg.getReceiver())) {
                    groupMessages.add(msg);
                }
            }
        }
        // 按时间排序
        groupMessages.sort(Comparator.comparing(StoredMessage::getTimestamp));
        if (limit > 0 && groupMessages.size() > limit) {
            return groupMessages.subList(Math.max(0, groupMessages.size() - limit), groupMessages.size());
        }
        return groupMessages;
    }

    /**
     * 获取指定群聊中指定成员的历史消息
     */
    public static List<StoredMessage> getGroupMemberMessages(String groupName, String member, int limit) {
        if (!initialized)
            initialize();

        List<StoredMessage> memberMessages = new ArrayList<>();
        for (List<StoredMessage> msgs : userMessageCache.values()) {
            for (StoredMessage msg : msgs) {
                if (msg.getType() == MessageType.GROUP
                        && groupName.equals(msg.getReceiver())
                        && member.equals(msg.getSender())) {
                    memberMessages.add(msg);
                }
            }
        }
        // 按时间排序
        memberMessages.sort(Comparator.comparing(StoredMessage::getTimestamp));
        if (limit > 0 && memberMessages.size() > limit) {
            return memberMessages.subList(Math.max(0, memberMessages.size() - limit), memberMessages.size());
        }
        return memberMessages;
    }

    /**
     * 添加消息到内存缓存
     */
    private static void addToCache(StoredMessage message) {
        // 发送者缓存
        userMessageCache.computeIfAbsent(message.getSender(), k -> new ArrayList<>()).add(message);

        // 接收者缓存（私聊消息）
        if (message.getReceiver() != null) {
            userMessageCache.computeIfAbsent(message.getReceiver(), k -> new ArrayList<>()).add(message);
        } else {
            // 群聊消息，添加到所有在线用户的缓存（这里简化处理）
            // 实际应用中可能需要维护群组成员列表
        }
    }

    /**
     * 从XML文件加载消息
     */
    private static void loadMessagesFromFile() {
        File file = new File(MESSAGE_FILE);
        if (!file.exists()) {
            createEmptyMessageFile();
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);

            NodeList messageNodes = doc.getElementsByTagName("message");
            long maxId = 0;

            for (int i = 0; i < messageNodes.getLength(); i++) {
                Element messageElem = (Element) messageNodes.item(i);

                long id = Long.parseLong(messageElem.getAttribute("id"));
                String sender = messageElem.getAttribute("sender");
                String receiver = messageElem.getAttribute("receiver");
                if (receiver.isEmpty())
                    receiver = null;
                String typeStr = messageElem.getAttribute("type");
                String timestampStr = messageElem.getAttribute("timestamp");
                String content = messageElem.getElementsByTagName("content").item(0).getTextContent();

                MessageType type = MessageType.valueOf(typeStr);
                LocalDateTime timestamp = LocalDateTime.parse(timestampStr, TIMESTAMP_FORMAT);

                StoredMessage message = new StoredMessage(id, sender, receiver, content, type, timestamp);
                addToCache(message);

                maxId = Math.max(maxId, id);
            }

            messageIdCounter.set(maxId + 1);
            System.out.println("已加载 " + messageNodes.getLength() + " 条历史消息");

        } catch (Exception e) {
            System.out.println("加载消息历史失败: " + e.getMessage());
            createEmptyMessageFile();
        }
    }

    /**
     * 保存单条消息到XML文件
     */
    private static void saveToFile(StoredMessage message) {
        try {
            File file = new File(MESSAGE_FILE);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc;

            if (file.exists()) {
                doc = builder.parse(file);
            } else {
                doc = builder.newDocument();
                Element root = doc.createElement("messages");
                doc.appendChild(root);
            }

            Element root = doc.getDocumentElement();
            Element messageElem = doc.createElement("message");

            messageElem.setAttribute("id", String.valueOf(message.getId()));
            messageElem.setAttribute("sender", message.getSender());
            messageElem.setAttribute("receiver", message.getReceiver() != null ? message.getReceiver() : "");
            messageElem.setAttribute("type", message.getType().toString());
            messageElem.setAttribute("timestamp", message.getTimestamp().format(TIMESTAMP_FORMAT));

            Element contentElem = doc.createElement("content");
            contentElem.setTextContent(message.getContent());
            messageElem.appendChild(contentElem);

            root.appendChild(messageElem);

            // 写入文件
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(doc), new StreamResult(file));

        } catch (Exception e) {
            System.out.println("保存消息失败: " + e.getMessage());
        }
    }

    /**
     * 创建空的消息文件
     */
    private static void createEmptyMessageFile() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element root = doc.createElement("messages");
            doc.appendChild(root);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(new File(MESSAGE_FILE)));

        } catch (Exception e) {
            System.out.println("创建消息文件失败: " + e.getMessage());
        }
    }

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        GROUP, // 群聊消息
        PRIVATE // 私聊消息
    }

    /**
     * 存储的消息对象
     */
    public static class StoredMessage {
        private long id;
        private String sender;
        private String receiver;
        private String content;
        private MessageType type;
        private LocalDateTime timestamp;

        public StoredMessage(long id, String sender, String receiver, String content, MessageType type,
                LocalDateTime timestamp) {
            this.id = id;
            this.sender = sender;
            this.receiver = receiver;
            this.content = content;
            this.type = type;
            this.timestamp = timestamp;
        }

        // Getters
        public long getId() {
            return id;
        }

        public String getSender() {
            return sender;
        }

        public String getReceiver() {
            return receiver;
        }

        public String getContent() {
            return content;
        }

        public MessageType getType() {
            return type;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null || getClass() != obj.getClass())
                return false;
            StoredMessage that = (StoredMessage) obj;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            String timeStr = timestamp.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
            if (type == MessageType.PRIVATE) {
                return String.format("[%s] [私聊] [%s -> %s]: %s", timeStr, sender, receiver, content);
            } else if(type == MessageType.GROUP) {
                return String.format("[%s] [群聊] [%s] %s: %s", timeStr, receiver ,sender, content);
            }
            return String.format("[%s] [未知类型] %s: %s", timeStr, sender, content);
        }
    }
}
