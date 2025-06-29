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
    private static final String PRIVATE_MESSAGE_FILE = "messages_private.xml";
    private static final String PUBLIC_MESSAGE_FILE = "messages_public.xml";
    private static final String GROUP_FILE_PREFIX = "group_";
    private static final String GROUP_FILE_SUFFIX = ".xml";
    private static final int GROUP_MESSAGE_LIMIT = 100;
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
    public static void saveMessage(String sender, String receiver, String content, MessageType type, String... extra) {
        if (!initialized)
            initialize();
        StoredMessage message = new StoredMessage(
                messageIdCounter.getAndIncrement(),
                sender,
                receiver,
                content,
                type,
                LocalDateTime.now());
        addToCache(message);
        if (type == MessageType.GROUP && receiver != null) {
            saveGroupMessageToFile(receiver, message);
        } else if (type == MessageType.PRIVATE) {
            savePrivateMessageToFile(message);
        } else if (type == MessageType.SUBGROUP && extra.length >= 2) {
            // extra[0]=groupName, extra[1]=subGroupId
            saveSubGroupMessageToFile(extra[0], extra[1], message);
        } else {
            savePublicMessageToFile(message);
        }
        System.out.println("消息已保存: " + sender + " -> " + (receiver != null ? receiver : "群聊") + ": " + content);
    }

    // 保存小组消息到文件
    private static void saveSubGroupMessageToFile(String groupName, String subGroupId, StoredMessage message) {
        String fileName = "group_" + groupName + "_subgroup_" + subGroupId + ".xml";
        try {
            File file = new File(fileName);
            Document doc;
            Element root;
            if (file.exists()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                doc = builder.parse(file);
                root = doc.getDocumentElement();
            } else {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                doc = builder.newDocument();
                root = doc.createElement("messages");
                doc.appendChild(root);
            }
            Element msgElem = doc.createElement("message");
            msgElem.setAttribute("id", String.valueOf(message.getId()));
            msgElem.setAttribute("sender", message.getSender());
            msgElem.setAttribute("receiver", message.getReceiver());
            msgElem.setAttribute("type", message.getType().name());
            msgElem.setAttribute("timestamp", message.getTimestamp().format(TIMESTAMP_FORMAT));
            msgElem.setTextContent(message.getContent());
            root.appendChild(msgElem);
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(file));
        } catch (Exception e) {
            System.out.println("小组消息保存失败: " + e.getMessage());
        }
    }

    // 查询小组历史消息
    public static List<StoredMessage> getSubGroupMessages(String groupName, String subGroupId, int limit) {
        List<StoredMessage> result = new ArrayList<>();
        String fileName = "group_" + groupName + "_subgroup_" + subGroupId + ".xml";
        File file = new File(fileName);
        if (!file.exists()) return result;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            NodeList msgNodes = doc.getElementsByTagName("message");
            for (int i = 0; i < msgNodes.getLength(); i++) {
                Element elem = (Element) msgNodes.item(i);
                StoredMessage msg = new StoredMessage(
                        Long.parseLong(elem.getAttribute("id")),
                        elem.getAttribute("sender"),
                        elem.getAttribute("receiver"),
                        elem.getTextContent(),
                        MessageType.valueOf(elem.getAttribute("type")),
                        LocalDateTime.parse(elem.getAttribute("timestamp"), TIMESTAMP_FORMAT)
                );
                result.add(msg);
            }
            result.sort(Comparator.comparing(StoredMessage::getTimestamp));
            if (limit > 0 && result.size() > limit) {
                return result.subList(Math.max(0, result.size() - limit), result.size());
            }
        } catch (Exception e) {
            System.out.println("小组消息读取失败: " + e.getMessage());
        }
        return result;
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

        List<StoredMessage> groupMessages = loadGroupMessagesFromFile(groupName);
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

        List<StoredMessage> groupMessages = loadGroupMessagesFromFile(groupName);
        List<StoredMessage> memberMessages = new ArrayList<>();
        for (StoredMessage msg : groupMessages) {
            if (msg.getSender().equals(member)) {
                memberMessages.add(msg);
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
            // 实际应用中可能需要维护���组成员列表
        }
    }

    /**
     * 从XML文件加载消息
     */
    private static void loadMessagesFromFile() {
        long maxId = 0;
        int totalMessages = 0;

        // 加载私聊消息
        maxId = Math.max(maxId, loadMessagesFromFile(PRIVATE_MESSAGE_FILE, "私聊"));
        totalMessages += getMessageCountFromFile(PRIVATE_MESSAGE_FILE);

        // 加载公共消息
        maxId = Math.max(maxId, loadMessagesFromFile(PUBLIC_MESSAGE_FILE, "公共"));
        totalMessages += getMessageCountFromFile(PUBLIC_MESSAGE_FILE);

        messageIdCounter.set(maxId + 1);
        System.out.println("已加载 " + totalMessages + " 条历史消息");
    }

    /**
     * 从指定XML文件加载消息
     */
    private static long loadMessagesFromFile(String fileName, String fileType) {
        File file = new File(fileName);
        if (!file.exists()) {
            createEmptyMessageFile(fileName);
            return 0;
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

            return maxId;

        } catch (Exception e) {
            System.out.println("加载" + fileType + "消息历史失败: " + e.getMessage());
            createEmptyMessageFile(fileName);
            return 0;
        }
    }

    /**
     * 获取文件中的消息数量
     */
    private static int getMessageCountFromFile(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            return 0;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            return doc.getElementsByTagName("message").getLength();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 保存私聊消息到私聊文件
     */
    private static void savePrivateMessageToFile(StoredMessage message) {
        saveMessageToFile(message, PRIVATE_MESSAGE_FILE, MessageType.PRIVATE);
    }

    /**
     * 保存公共消息到公共文件
     */
    private static void savePublicMessageToFile(StoredMessage message) {
        saveMessageToFile(message, PUBLIC_MESSAGE_FILE, MessageType.GROUP);
    }

    /**
     * 保存消息到指定文件
     * 修复版本：重新构建整个XML文档以避免空行累积
     */
    private static void saveMessageToFile(StoredMessage message, String fileName, MessageType filterType) {
        try {
            // 获取指定类型的现有消息
            List<StoredMessage> allMessages = getMessagesFromCacheByType(filterType);

            // 添加新消息
            allMessages.add(message);

            // 按ID排序确保顺序正确
            allMessages.sort(Comparator.comparing(StoredMessage::getId));

            // 重新构建整个XML文档
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element root = doc.createElement("messages");
            doc.appendChild(root);

            // 添加所有消息
            for (StoredMessage msg : allMessages) {
                Element messageElem = doc.createElement("message");

                messageElem.setAttribute("id", String.valueOf(msg.getId()));
                messageElem.setAttribute("sender", msg.getSender());
                messageElem.setAttribute("receiver", msg.getReceiver() != null ? msg.getReceiver() : "");
                messageElem.setAttribute("type", msg.getType().toString());
                messageElem.setAttribute("timestamp", msg.getTimestamp().format(TIMESTAMP_FORMAT));

                Element contentElem = doc.createElement("content");
                contentElem.setTextContent(msg.getContent());
                messageElem.appendChild(contentElem);

                root.appendChild(messageElem);
            }

            // 写入文件
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(doc), new StreamResult(new File(fileName)));

        } catch (Exception e) {
            System.out.println("保存消息到" + fileName + "失败: " + e.getMessage());
        }
    }

    /**
     * 从缓存中获取指定类型的消息
     */
    private static List<StoredMessage> getMessagesFromCacheByType(MessageType filterType) {
        List<StoredMessage> filteredMessages = new ArrayList<>();

        // 从所有用户的缓存中收集指定类型的消息，去重
        Set<Long> addedIds = new HashSet<>();
        for (List<StoredMessage> userMessages : userMessageCache.values()) {
            for (StoredMessage msg : userMessages) {
                if (msg.getType() == filterType && !addedIds.contains(msg.getId())) {
                    filteredMessages.add(msg);
                    addedIds.add(msg.getId());
                }
            }
        }

        return filteredMessages;
    }

    /**
     * 从缓存中获取所有消息（用于重建XML文件）
     */
    private static List<StoredMessage> getAllMessagesFromCache() {
        List<StoredMessage> allMessages = new ArrayList<>();

        // 从所有用户的缓存中收集消息，去重
        Set<Long> addedIds = new HashSet<>();
        for (List<StoredMessage> userMessages : userMessageCache.values()) {
            for (StoredMessage msg : userMessages) {
                if (!addedIds.contains(msg.getId())) {
                    allMessages.add(msg);
                    addedIds.add(msg.getId());
                }
            }
        }

        return allMessages;
    }

    // 保存群聊消息到单独的xml文件，最多保留100条
    private static void saveGroupMessageToFile(String groupName, StoredMessage message) {
        String fileName = GROUP_FILE_PREFIX + groupName + GROUP_FILE_SUFFIX;
        File file = new File(fileName);
        List<StoredMessage> messages = loadGroupMessagesFromFile(groupName);
        messages.add(message);
        // 保证最多100条
        if (messages.size() > GROUP_MESSAGE_LIMIT) {
            messages = messages.subList(messages.size() - GROUP_MESSAGE_LIMIT, messages.size());
        }
        // 写入xml
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement("messages");
            doc.appendChild(root);

            for (StoredMessage msg : messages) {
                Element messageElem = doc.createElement("message");
                messageElem.setAttribute("id", String.valueOf(msg.getId()));
                messageElem.setAttribute("sender", msg.getSender());
                messageElem.setAttribute("receiver", msg.getReceiver() != null ? msg.getReceiver() : "");
                messageElem.setAttribute("type", msg.getType().toString());
                messageElem.setAttribute("timestamp", msg.getTimestamp().format(TIMESTAMP_FORMAT));

                Element contentElem = doc.createElement("content");
                contentElem.setTextContent(msg.getContent());
                messageElem.appendChild(contentElem);

                root.appendChild(messageElem);
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(doc), new StreamResult(file));
        } catch (Exception e) {
            System.out.println("保存群聊消息失败: " + e.getMessage());
        }
    }

    // 读取群聊消息
    private static List<StoredMessage> loadGroupMessagesFromFile(String groupName) {
        String fileName = GROUP_FILE_PREFIX + groupName + GROUP_FILE_SUFFIX;
        File file = new File(fileName);
        List<StoredMessage> messages = new ArrayList<>();
        if (!file.exists()) {
            return messages;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);

            NodeList messageNodes = doc.getElementsByTagName("message");
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
                messages.add(message);
            }
        } catch (Exception e) {
            System.out.println("加载群聊消息失败: " + e.getMessage());
        }
        return messages;
    }

    /**
     * 创建空的消息文件
     */
    private static void createEmptyMessageFile(String fileName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element root = doc.createElement("messages");
            doc.appendChild(root);

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(new File(fileName)));

        } catch (Exception e) {
            System.out.println("创建消息文件" + fileName + "失败: " + e.getMessage());
        }
    }

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        PRIVATE, GROUP, PUBLIC, SUBGROUP // 新增SUBGROUP类型
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
            } else if (type == MessageType.GROUP) {
                return String.format("[%s] [群聊] [%s] %s: %s", timeStr, receiver, sender, content);
            }
            return String.format("[%s] [未知类型] %s: %s", timeStr, sender, content);
        }
    }
}
