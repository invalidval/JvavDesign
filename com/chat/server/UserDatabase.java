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
    private static ConcurrentHashMap<String, String> passwords = new ConcurrentHashMap<>(); // 存储用户名和密码
    private static Set<UserDatabaseObserver> observers = new CopyOnWriteArraySet<>();
    private static final String USER_FILE = "users.xml";

    public static void initialize() {
        // 从XML文件加载用户信息
        File file = new File(USER_FILE);
        if (!file.exists()) return;
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
                user.setOnline(false);
                // 读取好友
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
        user.setOnline(false); // 注册时默认离线，便于后续登录
        users.put(username, user);
        passwords.put(username, password);
        saveUsersToFile();
        notifyObservers(user);
        return true;
    }

    public static boolean loginUser(String username, String password) {
        User user = users.get(username);
        String storedPassword = passwords.get(username);
        if (user != null && storedPassword != null && storedPassword.equals(password) && !user.isOnline()) {
            user.setOnline(true);
            saveUsersToFile();
            return true;
        }
        return false;
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

    private static void saveUsersToFile() {
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
                // 保存好友
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

    // 观察者模式接口
    public interface UserDatabaseObserver {
        void onUserChanged(User user);
    }
}
