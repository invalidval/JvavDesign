package com.chat.server;

import com.chat.model.User;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.io.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

public class UserDatabase {
    private static ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, String> passwords = new ConcurrentHashMap<>();
    private static Set<UserDatabaseObserver> observers = new CopyOnWriteArraySet<>();
    private static final String USER_FILE = "users.xml";

    // 登录状态码
    public static final int LOGIN_SUCCESS = 0;
    public static final int LOGIN_USER_NOT_FOUND = 1;
    public static final int LOGIN_PASSWORD_ERROR = 2;
    public static final int LOGIN_ALREADY_ONLINE = 3;

    public static void initialize() {
        File file = new File(USER_FILE);
        if (!file.exists())
            return;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            NodeList userNodes = doc.getElementsByTagName("user");
            for (int i = 0; i < userNodes.getLength(); i++) {
                Element userElem = (Element) userNodes.item(i);
                String username = userElem.getAttribute("name");
                String password = userElem.getAttribute("password");
                User user = new User(username);
                user.setOnline(false); // 启动时全部设为离线
                NodeList friendNodes = userElem.getElementsByTagName("friend");
                for (int j = 0; j < friendNodes.getLength(); j++) {
                    Element friendElem = (Element) friendNodes.item(j);
                    user.addFriend(friendElem.getTextContent());
                }
                users.put(username, user);
                passwords.put(username, password);
            }
        } catch (Exception e) {
            System.out.println("用户数据加载失败: " + e.getMessage());
        }
    }

    public static void addObserver(UserDatabaseObserver observer) {
        observers.add(observer);
    }

    public static void removeObserver(UserDatabaseObserver observer) {
        observers.remove(observer);
    }

    private static void notifyObservers(User user) {
        for (UserDatabaseObserver observer : observers) {
            observer.onUserChanged(user);
        }
    }

    public static boolean registerUser(String username, String password) {
        if (users.containsKey(username)) {
            return false;
        }
        User user = new User(username);
        user.setOnline(false);
        users.put(username, user);
        passwords.put(username, password);
        saveUsersToFile();
        notifyObservers(user);
        return true;
    }

    public static int loginUser(String username, String password) {
        User user = users.get(username);
        String storedPassword = passwords.get(username);
        if (user == null || storedPassword == null) {
            return LOGIN_USER_NOT_FOUND;
        }
        if (!storedPassword.equals(password)) {
            return LOGIN_PASSWORD_ERROR;
        }

        // 检查用户是否已经在线（通过Server检查实际连接状态）
        boolean isActuallyOnline = com.chat.server.Server.getClientHandler(username) != null;

        if (isActuallyOnline) {
            // 用户已在线，需要踢下线处理，返回特殊状态码
            System.out.println("【loginUser】用户 " + username + " 已在线，准备踢下线处理");
            return LOGIN_ALREADY_ONLINE;
        }

        // 用户不在线，正常登录
        user.setOnline(true);
        saveUsersToFile();
        // 主动通知所有在线好友该用户上线
        notifyFriendsStatusChange(username, true);
        return LOGIN_SUCCESS;
    }

    // 新增：下线方法
    public static void logoutUser(String username) {
        User user = users.get(username);
        if (user != null && user.isOnline()) {
            user.setOnline(false);
            saveUsersToFile();
            notifyObservers(user);
            // 主动通知所有在线好友该用户下线
            notifyFriendsStatusChange(username, false);
        }
    }

    // 主动通知所有在线好友该用户上线/下线
    public static void notifyFriendsStatusChange(String username, boolean online) {
        User user = users.get(username);
        if (user == null)
            return;
        for (String friend : user.getFriends()) {
            com.chat.server.ClientHandler handler = com.chat.server.Server.getClientHandler(friend);
            if (handler != null) {
                handler.send("STATUS:" + username + ":" + (online ? "online" : "offline"));
            }
        }
    }

    // 新增：判断用户是否在线
    public static boolean isOnline(String username) {
        User user = users.get(username);
        return user != null && user.isOnline();
    }

    public static User getUser(String username) {
        return users.get(username);
    }

    public static boolean addFriend(String username, String friendName) {
        User user = users.get(username);
        User friend = users.get(friendName);
        if (user != null && friend != null) {
            user.addFriend(friendName);
            friend.addFriend(username);
            saveUsersToFile();
            notifyObservers(user);
            notifyObservers(friend);
            return true;
        }
        return false;
    }

    public static boolean deleteFriend(String username, String friendName) {
        User user = getUser(username);
        User friend = getUser(friendName);
        if (user != null && friend != null && user.getFriends().contains(friendName)) {
            user.getFriends().remove(friendName);
            friend.getFriends().remove(username);
            saveUsersToFile(); // 持久化
            return true;
        }
        return false;
    }

    public static void saveUsersToFile() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();
            Element root = doc.createElement("users");
            doc.appendChild(root);
            for (String username : users.keySet()) {
                Element userElem = doc.createElement("user");
                userElem.setAttribute("name", username);
                userElem.setAttribute("password", passwords.get(username));
                User user = users.get(username);
                for (String friend : user.getFriends()) {
                    Element friendElem = doc.createElement("friend");
                    friendElem.setTextContent(friend);
                    userElem.appendChild(friendElem);
                }
                root.appendChild(userElem);
            }
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(new File(USER_FILE)));
        } catch (Exception e) {
            System.out.println("用户数据保存失败: " + e.getMessage());
        }
    }

    public interface UserDatabaseObserver {
        void onUserChanged(User user);
    }
}
