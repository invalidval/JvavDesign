package com.chat.server;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import com.chat.model.User;
import com.chat.server.UserDatabase;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private Map<String, MessageHandlerStrategy> handlerStrategies = new HashMap<>();

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);

        // 注册策略，key为大写斜杠命令
        handlerStrategies.put("/F", new GetFriendsHandler());
        handlerStrategies.put("/A", new AddFriendHandler());
        handlerStrategies.put("/P", new PrivateMessageHandler());
        handlerStrategies.put("DEFAULT", new DefaultMessageHandler());
    }

    @Override
    public void run() {
        boolean loggedIn = false;
        try {
            while (!loggedIn) {
                String initialMessage = in.readLine();
                if (initialMessage == null) break;
                if (initialMessage.startsWith("/r")) {
                    String[] parts = initialMessage.trim().split("\\s+");
                    if (parts.length < 3) {
                        out.println("ERROR: 格式为 /r 用户名 密码");
                        continue;
                    }
                    String username = parts[1];
                    String password = parts[2];
                    if (UserDatabase.registerUser(username, password)) {
                        out.println("SUCCESS: 注册成功！请用 /l 登录。");
                    } else {
                        out.println("ERROR: 用户名已存在，请尝试其他用户名。");
                    }
                } else if (initialMessage.startsWith("/l")) {
                    String[] parts = initialMessage.trim().split("\\s+");
                    if (parts.length < 3) {
                        out.println("ERROR: 格式为 /l 用户名 密码");
                        continue;
                    }
                    String username = parts[1];
                    String password = parts[2];
                    if (UserDatabase.loginUser(username, password)) {
                        this.username = username;
                        Server.addClient(username, this);
                        loggedIn = true;
                        out.println("SUCCESS: 登录成功！您可以开始聊天了。");
                        out.println("使用说明：");
                        out.println("/a 好友用户名 —— 添加好友");
                        out.println("/f —— 查看好友列表");
                        out.println("/p 用户名 消息内容 —— 私聊");
                        out.println("直接输入内容为群聊消息");
                    } else {
                        out.println("ERROR: 用户名或密码错误，请重试。");
                    }
                } else {
                    out.println("请先注册或登录！");
                }
            }

            String message;
            while ((message = in.readLine()) != null) {
                String command = message.startsWith("/") ? message.split("\\s+")[0].toUpperCase() : "DEFAULT";
                MessageHandlerStrategy strategy = handlerStrategies.getOrDefault(command, handlerStrategies.get("DEFAULT"));
                strategy.handle(message, this);
            }
        } catch (IOException e) {
            System.out.println(username + " 已断开连接");
        } finally {
            if (username != null) {
                Server.removeClient(username);
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void send(String message) {
        synchronized (out) { // 确保输出流的线程安全
            out.println(message);
        }
    }

    // 策略模式接口
    interface MessageHandlerStrategy {
        void handle(String message, ClientHandler handler);
    }

    // 各种消息处理策略实现
    class GetFriendsHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            // 现在 /f 不需要参数
            if (username == null) {
                handler.send("ERROR: 未登录");
                return;
            }
            User currentUser = UserDatabase.getUser(username);
            if (currentUser != null) {
                handler.send(String.join(",", currentUser.getFriends()));
            } else {
                handler.send("ERROR: 用户不存在");
            }
        }
    }

    class AddFriendHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 2) {
                handler.send("ERROR: 格式为 /a 好友用户名");
                return;
            }
            String friendName = parts[1];
            User currentUser = UserDatabase.getUser(username);
            if (currentUser != null && UserDatabase.addFriend(username, friendName)) {
                handler.send("SUCCESS: 好友添加成功");
            } else {
                handler.send("ERROR: 添加好友失败");
            }
        }
    }

    class PrivateMessageHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+", 3);
            if (parts.length < 3) {
                handler.send("ERROR: 格式为 /p 用户名 消息内容");
                return;
            }
            String targetUser = parts[1];
            String privateMessage = parts[2];
            Server.sendPrivateMessage(username, targetUser, privateMessage);
        }
    }

    class DefaultMessageHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            System.out.println(username + ": " + message);
            Server.broadcast(username + ": " + message);
        }
    }
}

