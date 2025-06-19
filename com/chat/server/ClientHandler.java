package com.chat.server;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.chat.model.*;
import com.chat.server.UserDatabase;
import com.chat.server.GroupDatabase;
import com.chat.server.MessageStorage;

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
        handlerStrategies.put("/D", new DeleteFriendHandler());
        handlerStrategies.put("/CG", new CreateGroupHandler()); // 新增
        handlerStrategies.put("/GLIST", new GroupListHandler()); // 新增
        handlerStrategies.put("/GS", new GroupSendHandler()); // 新增
        handlerStrategies.put("/ONLINE", new OnlineStatusHandler()); // 新增
        handlerStrategies.put("/H", new HistoryMessageHandler()); // 历史消息查询
        handlerStrategies.put("/JG", new JoinGroupHandler()); // 新增加入群聊处理器
        handlerStrategies.put("/HG", new GroupHistoryMessageHandler()); // 新增群聊历史消息查询
        handlerStrategies.put("DEFAULT", new DefaultMessageHandler());
    }

    @Override
    public void run() {
        boolean loggedIn = false;
        try {
            while (!loggedIn) {
                String initialMessage = in.readLine();

                if (initialMessage == null)
                    break;
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
                    int loginStatus = UserDatabase.loginUser(username, password);
                    if (loginStatus == UserDatabase.LOGIN_SUCCESS) {
                        this.username = username;
                        Server.addClient(username, this);
                        loggedIn = true;
                        out.println("SUCCESS: 登录成功！您可以开始聊天了。");

                        // 显示最近的消息记录
                        showRecentMessagesOnLogin();

                    } else if (loginStatus == UserDatabase.LOGIN_ALREADY_ONLINE) {
                        out.println("ERROR: 该用户已在线，不允许重复登录。");
                    } else if (loginStatus == UserDatabase.LOGIN_PASSWORD_ERROR) {
                        out.println("ERROR: 密码错误，请重试。");
                    } else if (loginStatus == UserDatabase.LOGIN_USER_NOT_FOUND) {
                        out.println("ERROR: 用户不存在，请先注册。");
                    } else {
                        out.println("ERROR: 登录失败。");
                    }
                } else {
                    // 未登录时，所有非注册/登录命令都直接提示，不做任何数据库操作
                    out.println("请先注册或登录！");
                }
            }

            // 只有登录后才允许执行其他命令
            String message;
            while ((message = in.readLine()) != null) {
                if (username == null) {
                    // 理论上不会到这里，但保险起见
                    out.println("请先注册或登录！");
                    continue;
                }
                String command = message.startsWith("/") ? message.split("\\s+")[0].toUpperCase() : "DEFAULT";
                MessageHandlerStrategy strategy = handlerStrategies.getOrDefault(command,
                        handlerStrategies.get("DEFAULT"));
                strategy.handle(message, this);
            }
        } catch (IOException e) {
            out.println(username + " 已断开连接");
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

    /**
     * 强制断开客户端连接
     */
    public void forceDisconnect() {
        try {
            if (username != null) {
                User user = UserDatabase.getUser(username);
                if (user != null) {
                    user.setOnline(false);
                }
                Server.removeClient(username);
            }
            socket.close();
        } catch (IOException e) {
            System.out.println("强制断开连接时发生错误: " + e.getMessage());
        }
    }

    /**
     * 登录时显示最近的消息记录
     */
    private void showRecentMessagesOnLogin() {
        var messages = MessageStorage.getUserMessages(username, 5); // 显示最近5条消息
        if (!messages.isEmpty()) {
            out.println("=== 最近消息记录 ===");
            // 按时间正序显示（最早的在前）
            for (int i = messages.size() - 1; i >= 0; i--) {
                out.println(messages.get(i).toString());
            }
            out.println("==================");
        }
    }

    // 策略模式接口
    interface MessageHandlerStrategy {
        void handle(String message, ClientHandler handler);
    }

    // 各种消息处理策略实现
    class GetFriendsHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            if (username == null) {
                handler.send("ERROR: 未登录");
                return;
            }
            User currentUser = UserDatabase.getUser(username);
            if (currentUser != null) {
                handler.send("FRIENDS:" + String.join(",", currentUser.getFriends())); // 添加 FRIENDS: 标识符
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
                // 主动通知被加方刷新好友列表
                ClientHandler friendHandler = Server.getClientHandler(friendName);
                if (friendHandler != null) {
                    friendHandler.send("FRIENDS:" + String.join(",", UserDatabase.getUser(friendName).getFriends()));
                }
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

            // 保存私聊消息到存储
            MessageStorage.saveMessage(username, targetUser, privateMessage, MessageStorage.MessageType.PRIVATE);

            Server.sendPrivateMessage(username, targetUser, privateMessage);
        }
    }

    class DeleteFriendHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 2) {
                handler.send("ERROR: 格式为 /d 好友用户名");
                return;
            }
            String friendName = parts[1];
            User currentUser = UserDatabase.getUser(username);
            if (currentUser != null && UserDatabase.deleteFriend(username, friendName)) {
                handler.send("SUCCESS: 好友删除成功");
            } else {
                handler.send("ERROR: 删除好友失败");
            }
        }
    }

    // 创建群聊
    class CreateGroupHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 3) {
                handler.send("ERROR: 格式为 /cg 群名 成员1 成员2 ...");
                return;
            }
            String groupName = parts[1];
            Set<String> members = new HashSet<>();
            members.add(username);
            for (int i = 2; i < parts.length; i++) {
                if (!parts[i].equals(username))
                    members.add(parts[i]);
            }
            boolean ok = GroupDatabase.createGroup(groupName, members);
            if (ok) {
                handler.send("SUCCESS: 创建群聊成功");
                // 主动通知所有被拉入群聊的在线成员刷新群聊列表
                for (String member : members) {
                    if (!member.equals(username)) {
                        ClientHandler memberHandler = Server.getClientHandler(member);
                        if (memberHandler != null) {
                            Set<String> groups = GroupDatabase.getGroupsOfUser(member);
                            memberHandler.send("GROUPS:" + String.join(",", groups));
                        }
                    }
                }
            } else {
                handler.send("ERROR: 群聊已存在或成员无效");
            }
        }
    }

    // 获取自己所在群
    class GroupListHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            Set<String> groups = GroupDatabase.getGroupsOfUser(username);
            handler.send("GROUPS:" + String.join(",", groups));
        }
    }

    // 群聊消息发送
    class GroupSendHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+", 3);
            if (parts.length < 3) {
                handler.send("ERROR: 格式为 /gs 群名 消息内容");
                return;
            }
            String groupName = parts[1];
            String msg = parts[2];
            if (!GroupDatabase.isUserInGroup(groupName, username)) {
                handler.send("ERROR: 你不在该群聊中");
                return;
            }

            // 保存群聊消息到存储系统
            MessageStorage.saveMessage(username, groupName, msg, MessageStorage.MessageType.GROUP);

            GroupDatabase.sendGroupMessage(groupName, username, msg);
        }
    }

    // 在线状态查询
    class OnlineStatusHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            Set<String> onlineUsers = new HashSet<>();
            for (int i = 1; i < parts.length; i++) {
                String user = parts[i];
                if (Server.getClientHandler(user) != null) {
                    onlineUsers.add(user);
                }
            }
            handler.send("ONLINE:" + String.join(",", onlineUsers));
        }
    }

    class HistoryMessageHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            if (username == null) {
                handler.send("ERROR: 未登录");
                return;
            }

            String[] parts = message.trim().split("\\s+");

            if (parts.length == 1) {
                // /h - 查看最近20条消息
                showRecentMessages(handler, 20);
            } else if (parts.length == 2) {
                try {
                    // /h 数字 - 查看指定数量的消息
                    int count = Integer.parseInt(parts[1]);
                    if (count > 0 && count <= 100) {
                        showRecentMessages(handler, count);
                    } else {
                        handler.send("ERROR: 消息数量应在1-100之间");
                    }
                } catch (NumberFormatException e) {
                    // /h 用户名 - 查看与指定用户的私聊记录
                    String targetUser = parts[1];
                    showPrivateMessages(handler, targetUser, 20);
                }
            } else if (parts.length == 3) {
                // /h 用户名 数字 - 查看与指定用户的指定数量私聊记录
                String targetUser = parts[1];
                try {
                    int count = Integer.parseInt(parts[2]);
                    if (count > 0 && count <= 100) {
                        showPrivateMessages(handler, targetUser, count);
                    } else {
                        handler.send("ERROR: 消息数量应在1-100之间");
                    }
                } catch (NumberFormatException e) {
                    handler.send("ERROR: 格式为 /h [用户名] [数量]");
                }
            } else {
                handler.send("ERROR: 格式为 /h [用户名] [数量]");
                handler.send("使用说明：");
                handler.send("/h          - 查看最近20条消息");
                handler.send("/h 数量     - 查看指定数量的消息");
                handler.send("/h 用户名   - 查看与指定用户的私聊记录");
                handler.send("/h 用户名 数量 - 查看与指定用户的指定数量私聊记录");
            }
        }

        private void showRecentMessages(ClientHandler handler, int count) {
            var messages = MessageStorage.getUserMessages(username, count);
            if (messages.isEmpty()) {
                handler.send("暂无消息记录");
                return;
            }

            handler.send("=== 最近 " + messages.size() + " 条消息记录 ===");
            // 按时间正序显示（最早的在前）
            for (int i = messages.size() - 1; i >= 0; i--) {
                handler.send(messages.get(i).toString());
            }
            handler.send("==================");
        }

        private void showPrivateMessages(ClientHandler handler, String targetUser, int count) {
            var messages = MessageStorage.getPrivateMessages(username, targetUser, count);
            if (messages.isEmpty()) {
                handler.send("与 " + targetUser + " 暂无私聊记录");
                return;
            }

            handler.send("=== 与 " + targetUser + " 的私聊记录 (" + messages.size() + " 条) ===");
            for (var msg : messages) {
                handler.send(msg.toString());
            }
            handler.send("==================");
        }
    }

    class DefaultMessageHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            // 保存群聊消息到存储
            MessageStorage.saveMessage(username, null, message, MessageStorage.MessageType.GROUP);

            System.out.println(username + ": " + message);
            Server.broadcast(username + ": " + message);
        }
    }

    // 加入群聊
    class JoinGroupHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 2) {
                handler.send("ERROR: 格式为 /jg 群名");
                return;
            }
            String groupName = parts[1];
            Group group = GroupDatabase.getGroup(groupName);
            if (group == null) {
                handler.send("ERROR: 群聊不存在");
                return;
            }
            if (group.getMembers().contains(UserDatabase.getUser(username))) {
                handler.send("ERROR: 你已在该群聊中");
                return;
}
            group.getMembers().add(UserDatabase.getUser(username));
            group.notifyObservers(); // 通知观察者更新群聊状态
            handler.send("SUCCESS: 加入群聊成功");
        }
    }

    class GroupHistoryMessageHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            if (username == null) {
                handler.send("ERROR: 未登录");
                return;
            }
            String[] parts = message.trim().split("\\s+");
            if (parts.length == 2) {
                // /hg 群名
                String groupName = parts[1];
                showGroupMessages(handler, groupName, 20);
            } else if (parts.length == 3) {
                // /hg 群名 数量
                String groupName = parts[1];
                try {
                    int count = Integer.parseInt(parts[2]);
                    if (count > 0 && count <= 100) {
                        showGroupMessages(handler, groupName, count);
                    } else {
                        handler.send("ERROR: 消息数量应在1-100之间");
                    }
                } catch (NumberFormatException e) {
                    // /hg 群名 成员名
                    String member = parts[2];
                    showGroupMemberMessages(handler, groupName, member, 20);
                }
            } else if (parts.length == 4) {
                // /hg 群名 成员名 数量
                String groupName = parts[1];
                String member = parts[2];
                try {
                    int count = Integer.parseInt(parts[3]);
                    if (count > 0 && count <= 100) {
                        showGroupMemberMessages(handler, groupName, member, count);
                    } else {
                        handler.send("ERROR: 消息数量应在1-100之间");
                    }
                } catch (NumberFormatException e) {
                    handler.send("ERROR: 格式为 /hg 群名 [成员名] [数量]");
                }
            } else {
                handler.send("ERROR: 格式为 /hg 群名 [成员名] [数量]");
                handler.send("使用说明：");
                handler.send("/hg 群名                - 查看该群最近20条消息");
                handler.send("/hg 群名 数量           - 查看该群指定数量的消息");
                handler.send("/hg 群名 成员名         - 查看该群指定成员的最近20条消息");
                handler.send("/hg 群名 成员名 数量    - 查看该群指定成员的指定数量消息");
            }
        }

        private void showGroupMessages(ClientHandler handler, String groupName, int count) {
            var messages = MessageStorage.getGroupMessages(groupName, count);
            if (messages.isEmpty()) {
                handler.send("群聊 " + groupName + " 暂无消息记录");
                return;
            }
            handler.send("=== 群聊 " + groupName + " 的消息记录 (" + messages.size() + " 条) ===");
            for (var msg : messages) {
                handler.send(msg.toString());
            }
            handler.send("==================");
        }

        private void showGroupMemberMessages(ClientHandler handler, String groupName, String member, int count) {
            var messages = MessageStorage.getGroupMemberMessages(groupName, member, count);
            if (messages.isEmpty()) {
                handler.send("群聊 " + groupName + " 中成员 " + member + " 暂无消息记录");
                return;
            }
            handler.send("=== 群聊 " + groupName + " 中 " + member + " 的消息记录 (" + messages.size() + " 条) ===");
            for (var msg : messages) {
                handler.send(msg.toString());
            }
            handler.send("==================");
        }
    }
}