package com.chat.server;

import java.io.*;
import java.net.Socket;
import java.util.*;

import com.chat.file.FileDatabase;
import com.chat.file.FileInfo;
import com.chat.model.*;
import com.chat.server.UserDatabase;
import com.chat.server.GroupDatabase;
import com.chat.server.MessageStorage;

public class ClientHandler extends Thread implements UserObserver {
    private Socket socket;
    private DataInputStream in; // 使用DataInputStream
    private DataOutputStream out; // 使用DataOutputStream
    private String username;
    private Map<String, MessageHandlerStrategy> handlerStrategies = new HashMap<>();

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        // 使用DataInputStream和DataOutputStream
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());

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
        handlerStrategies.put("/FILE", new FileReceiveHandler());// 添加文件处理器注册
        handlerStrategies.put("/DOWNLOAD", new FileDownloadHandler());
        handlerStrategies.put("DEFAULT", new DefaultMessageHandler());
    }

    public void run() {
        boolean loggedIn = false;
        try {
            while (!loggedIn) {
                String initialMessage = in.readUTF(); // readUTF

                if (initialMessage == null)
                    break;

                if (initialMessage.startsWith("/r")) {
                    String[] parts = initialMessage.trim().split("\\s+");
                    if (parts.length < 3) {
                        out.writeUTF("ERROR: 格式为 /r 用户名 密码"); // writeUTF
                        continue;
                    }
                    String username = parts[1];
                    String password = parts[2];
                    if (UserDatabase.registerUser(username, password)) {
                        out.writeUTF("SUCCESS: 注册成功！请用 /l 登录。"); // writeUTF
                    } else {
                        out.writeUTF("ERROR: 用户名已存在，请尝试其他用户名。"); // writeUTF
                    }

                } else if (initialMessage.startsWith("/l")) {
                    String[] parts = initialMessage.trim().split("\\s+");
                    if (parts.length < 3) {
                        out.writeUTF("ERROR: 格式为 /l 用户名 密码"); // writeUTF
                        continue;
                    }
                    String tryUsername = parts[1];
                    String password = parts[2];
                    int loginStatus = UserDatabase.loginUser(tryUsername, password);

                    if (loginStatus == UserDatabase.LOGIN_SUCCESS) {
                        this.username = tryUsername;
                        User user = UserDatabase.getUser(username);
                        if (user != null) {
                            user.addObserver(this); // ✅ 注册观察者
                        }

                        Server.addClient(username, this);
                        loggedIn = true;
                        out.writeUTF("SUCCESS: 登录成功！您可以开始聊天了。"); // writeUTF

                        showRecentMessagesOnLogin(); // 显示历史消息

                    } else if (loginStatus == UserDatabase.LOGIN_ALREADY_ONLINE) {
                        out.writeUTF("ERROR: 该用户已在线，不允许重复登录。"); // writeUTF
                    } else if (loginStatus == UserDatabase.LOGIN_PASSWORD_ERROR) {
                        out.writeUTF("ERROR: 密码错误，请重试。"); // writeUTF
                    } else if (loginStatus == UserDatabase.LOGIN_USER_NOT_FOUND) {
                        out.writeUTF("ERROR: 用户不存在，请先注册。"); // writeUTF
                    } else {
                        out.writeUTF("ERROR: 登录失败。"); // writeUTF
                    }

                } else {
                    out.writeUTF("请先注册或登录！"); // writeUTF
                }
            }

            String message;
            while ((username != null) && (message = in.readUTF()) != null) { // readUTF
                String command = message.startsWith("/") ? message.split("\\s+")[0].toUpperCase() : "DEFAULT";
                MessageHandlerStrategy strategy = handlerStrategies.getOrDefault(command,
                        handlerStrategies.get("DEFAULT"));
                strategy.handle(message, this);
            }

        } catch (EOFException e) {
            System.out.println(username + " 客户端关闭了连接。");
        } catch (IOException e) {
            System.out.println(username + " 已断开连接: " + e.getMessage());
        } finally {
            if (username != null) {
                ClientHandler current = Server.getClient(username);
                if (current == this) {
                    Server.removeClient(username);
                } else {
                    System.out.println("【finally】当前不是最新连接，跳过 removeClient");
                }

                // ✅ 注销观察者
                User user = UserDatabase.getUser(username);
                if (user != null) {
                    user.removeObserver(this);
                }
            }
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("【finally】关闭Socket错误: " + e.getMessage());
            }
        }
    }


    @Override
    public void onUserStatusChanged(User user) {
        // 好友上线/下线时回调，发送状态更新
        send("STATUS:" + user.getName() + ":" + (user.isOnline() ? "online" : "offline"));
    }


    /**
     * 发送消息给客户端，线程安全
     */
    public void send(String message) {
        synchronized (out) {
            try {
                out.writeUTF(message);
                out.flush();
            } catch (IOException e) {
                System.err.println("向 " + username + " 发送消息失败: " + e.getMessage());
            }
        }
    }

    /**
     * 强制断开客户端连接（不移除，交由 run() 的 finally 处理）
     */
    public void forceDisconnect() {
        System.out.println("【forceDisconnect】开始执行，username=" + username);
        try {
            socket.close();
            System.out.println("【forceDisconnect】socket.close() 执行完成");
        } catch (IOException e) {
            System.out.println("【forceDisconnect】关闭Socket错误: " + e.getMessage());
        }
    }


    /**
     * 登录时显示最近的消息记录
     */
    private void showRecentMessagesOnLogin() {
        var messages = MessageStorage.getUserMessages(username, 5); // 显示最近5条消息
        if (!messages.isEmpty()) {
            send("=== 最近消息记录 ===");
            // 按时间正序显示（最早的在前）
            for (int i = messages.size() - 1; i >= 0; i--) {
                send(messages.get(i).toString());
            }

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
                handler.send("=== 暂无消息记录 ===");
                return;
            }

            handler.send("=== 最近 " + messages.size() + " 条消息记录 ===");
            // 按时间正序显示（最早的在前）
            for (int i = messages.size() - 1; i >= 0; i--) {
                handler.send(messages.get(i).toString());
            }

        }

        private void showPrivateMessages(ClientHandler handler, String targetUser, int count) {
            var messages = MessageStorage.getPrivateMessages(username, targetUser, count);
            if (messages.isEmpty()) {
                handler.send("=== 与 " + targetUser + " 暂无私聊记录 ===");
                return;
            }

            handler.send("=== 与 " + targetUser + " 的私聊记录 (" + messages.size() + " 条) ===");
            for (var msg : messages) {
                handler.send(msg.toString());
            }

        }
    }

    class DefaultMessageHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            // 保存群聊消息到存储
//            MessageStorage.saveMessage(username, null, message, MessageStorage.MessageType.GROUP);
//
//            System.out.println(username + ": " + message);
//            Server.broadcast(username + ": " + message);
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
                handler.send("=== 群聊 " + groupName + " 暂无消息记录 ===");
                return;
            }
            handler.send("=== 群聊 " + groupName + " 的消息记录 (" + messages.size() + " 条) ===");
            for (var msg : messages) {
                handler.send(msg.toString());
            }

        }

        private void showGroupMemberMessages(ClientHandler handler, String groupName, String member, int count) {
            var messages = MessageStorage.getGroupMemberMessages(groupName, member, count);
            if (messages.isEmpty()) {
                handler.send("=== 群聊 " + groupName + " 中成员 " + member + " 暂无消息记录 ===");
                return;
            }
            handler.send("=== 群聊 " + groupName + " 中 " + member + " 的消息记录 (" + messages.size() + " 条) ===");
            for (var msg : messages) {
                handler.send(msg.toString());
            }

        }
    }

    class FileReceiveHandler implements MessageHandlerStrategy {
        @Override
        public void handle(String message, ClientHandler handler) {
            System.out.println("接收到文件上传请求: ");
            try {
                // 解析文件信息
                String[] parts = message.trim().split("\\s+");
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                String targetUser = parts[3];
                boolean isGroup = Boolean.parseBoolean(parts[4]);

                // 创建目标目录
                String basePath = isGroup ? "files/groups/" : "files/private/";
                String dirPath = basePath + (isGroup ? targetUser : username + "_" + targetUser);
                File dir = new File(dirPath);
                if (!dir.exists()) {
                    boolean created = dir.mkdirs(); // 检查目录创建是否成功
                    if (!created) {
                        System.err.println("无法创建目录: " + dirPath);
                        handler.send("ERROR: 无法创建文件目录");
                        return;
                    }
                }

                // 生成文件ID和保存路径
                String fileId = UUID.randomUUID().toString();
                String filePath = dirPath + "/" + fileName;
                File file = new File(filePath);
                System.out.println("正在保存文件到: " + filePath); // 添加日志

                // 接收文件数据
                // 关键：使用handler持有的统一输入流，而不是从socket获取新流
                DataInputStream dataIn = handler.in;
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buffer = new byte[8192];
                long remainingBytes = fileSize;

                while (remainingBytes > 0) {
                    int read = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remainingBytes));
                    if (read == -1) break;
                    fos.write(buffer, 0, read);
                    remainingBytes -= read;
                }

                fos.close();

                // 按照正确的构造函数参数顺序创建FileInfo对象
                FileInfo fileInfo = new FileInfo(
                        fileId,          // String fileId
                        fileName,        // String fileName
                        fileSize,        // long fileSize
                        username,        // String sender
                        targetUser,      // String receiver
                        isGroup,         // boolean isGroup
                        filePath        // String filePath
                );

                FileDatabase.addFile(fileInfo);

                // 发送文件通知
                String notifyMsg = String.format("FILE_NOTIFY:%s:%s:%d:%s:%s:%s",
                        fileId, fileName, fileSize, username, targetUser, isGroup ? "group" : "private");

                if (isGroup) {
                    // 群文件，通知所有群成员
                    Set<String> members = GroupDatabase.getGroupMembers(targetUser);
                    if (members != null) {
                        for (String member : members) {
                            if (!member.equals(username)) {
                                ClientHandler memberHandler = Server.getClientHandler(member);
                                if (memberHandler != null) {
                                    memberHandler.send(notifyMsg);
                                }
                            }
                        }
                    }
                } else {
                    // 私聊文件，只通知目标用户
                    ClientHandler targetHandler = Server.getClientHandler(targetUser);
                    if (targetHandler != null) {
                        targetHandler.send(notifyMsg);
                    }
                }

                handler.send("SUCCESS: 文件上传成功");

            } catch (IOException e) {
                e.printStackTrace();
                handler.send("ERROR: 文件上传失败: " + e.getMessage());
            }
        }
    }

    class FileDownloadHandler implements MessageHandlerStrategy {
        @Override
        public void handle(String message, ClientHandler handler) {
            System.out.println("接收到文件下载请求: " + message);
            // 关键：使用handler持有的统一输出流，并确保线程安全
            DataOutputStream dos = handler.out;
            synchronized (dos) {
                try {
                    String[] parts = message.trim().split("\\s+", 2);
                    if (parts.length < 2) {
                        dos.writeUTF("ERROR:无效的下载请求");
                        dos.flush();
                        return;
                    }

                    String fileId = parts[1];
                    FileInfo fileInfo = FileDatabase.getFileInfo(fileId);
                    if (fileInfo == null) {
                        dos.writeUTF("ERROR:文件不存在");
                        dos.flush();
                        return;
                    }

                    File file = new File(fileInfo.getPath());
                    if (!file.exists()) {
                        dos.writeUTF("ERROR:文件不存在");
                        dos.flush();
                        return;
                    }

                    // 发送文件数据
                    dos.writeUTF("FILE_DATA");
                    dos.writeUTF("FILE_DATA");
                    dos.writeUTF(fileInfo.getName());
                    dos.writeLong(file.length());


                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            dos.write(buffer, 0, bytesRead);
                        }
                        dos.flush();
                        System.out.println("服务端上传完成: " + fileInfo.getName());
                    }

                    // === 关键：此处不要再用 handler.send()/out.println() 发送任何内容 ===

                } catch (IOException e) {
                    try {
                        // 异常也通过同一个流发送
                        dos.writeUTF("ERROR:文件下载失败: " + e.getMessage());
                        dos.flush();
                    } catch (IOException ex) {
                        // ignore
                    }
                    System.out.println("文件下载处理异常: " + e.getMessage());
                }
            }
        }
    }
}
