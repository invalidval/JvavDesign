package com.chat.server;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.NewFunctions.audio.AudioPacket;
import com.chat.NewFunctions.audio.Packetizer;
import com.chat.file.FileDatabase;
import com.chat.file.FileInfo;
import com.chat.model.*;
import com.chat.server.UserDatabase;
import com.chat.server.GroupDatabase;
import com.chat.server.MessageStorage;
import com.chat.NewFunctions.audio.VoiceChatManager;

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
       // handlerStrategies.put("/VOICE", new VoiceChatHandler(new VoiceChatManager())); // 添加语音处理器注册
        // 新增：图片接收与转发处理
        handlerStrategies.put("/IMAGE", new ImageReceiveHandler());
        handlerStrategies.put("/SG_CREATE", new SubGroupCreateHandler());
        handlerStrategies.put("/SG_INVITE", new SubGroupInviteHandler());
        handlerStrategies.put("/SG_JOIN", new SubGroupJoinHandler());
        handlerStrategies.put("/SG_EXIT", new SubGroupExitHandler());
        handlerStrategies.put("/SG_MSG", new SubGroupMsgHandler());
        // 新增：小组列表查询命令
        handlerStrategies.put("/SG_LIST", new SubGroupListHandler());
        handlerStrategies.put("/SG_HISTORY", new SubGroupHistoryHandler());
        handlerStrategies.put("/SG_ACCEPT", new SubGroupAcceptHandler()); // 新增小组邀请接受处理器
        handlerStrategies.put("/VOICE_INVITE", new VoiceInviteHandler());
        handlerStrategies.put("/VOICE_ACCEPT", new VoiceAcceptHandler());
    }

    public void run() {
        boolean loggedIn = false;
        try {
            while (!loggedIn) {
                String initialMessage = in.readUTF(); // readUTF

                if (initialMessage == null)
                    break;

                // === 支持独立socket直接处理/FILE和/DOWNLOAD ===
                String cmd = initialMessage.trim().split("\\s+")[0].toUpperCase();
                if ("/FILE".equals(cmd) || "/DOWNLOAD".equals(cmd)) {
                    MessageHandlerStrategy strategy = handlerStrategies.get(cmd);
                    if (strategy != null) {
                        strategy.handle(initialMessage, this);
                    }
                    // 处理完直接关闭连接
                    return;
                }

                if (initialMessage.startsWith("/r")) {
                    String[] parts = initialMessage.trim().split("\\s+");
                    if (parts.length < 3) {
                        out.writeUTF("ERROR: ���������为 /r 用户名 密码"); // writeUTF
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
                // 新增：处理小组邀请通知的客户端响应
                if (message.startsWith("/sg_invite_accept ")) {
                    // /sg_invite_accept 群名 小组ID
                    String[] parts = message.trim().split("\\s+");
                    if (parts.length >= 3) {
                        String groupName = parts[1];
                        String subGroupId = parts[2];
                        var group = com.chat.server.GroupDatabase.getGroup(groupName);
                        if (group == null) {
                            send("ERROR: 群聊不存在");
                        } else {
                            boolean ok = com.chat.NewFunctions.subgroup.SubGroupManager.acceptInviteToSubGroup(group, subGroupId, username);
                            if (ok) {
                                send("SUCCESS: 已加入小组");
                                // 通知所有小组成员刷新小组列表
                                for (var sg : group.getSubGroups()) {
                                    for (String member : sg.getMembers()) {
                                        ClientHandler ch = com.chat.server.Server.getClientHandler(member);
                                        if (ch != null) {
                                            ch.send("SG_LIST:" + com.chat.NewFunctions.subgroup.SubGroupManager.buildSubGroupListString(group));
                                        }
                                    }
                                }
                            } else {
                                send("ERROR: 接受邀请失败（可能未被邀请或参数有误）");
                            }
                        }
                    } else {
                        send("ERROR: 格式为 /sg_invite_accept 群名 小组ID");
                    }
                    continue;
                }
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
        // 好友上线/下线时��调，发送状态更新
        send("STATUS:" + user.getName() + ":" + (user.isOnline() ? "online" : "offline"));
    }


    /**
     * 发送消息给客户端，线程安全
     */
    public void send(String message) {
        synchronized (out) {
            try {
                System.out.println("[SEND] to " + username + ": " + message);
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
    interface MessageHandlerStrategy1 {
        void handle(String[] messages, ClientHandler handler);
    }

    // 各种消息处理策略��现
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
                handler.send("ERROR: 群���������已存在或成员无效");
            }
        }
    }

    // 获取自己群
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
                    // /h 用户名 - 查看��指��用户的私聊记录
                    String targetUser = parts[1];
                    showPrivateMessages(handler, targetUser, 20);
                }
            } else if (parts.length == 3) {
                // /h 用户名 数字 - ���看与指定用户的指定数量私聊记录
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
                handler.send("/h 数量     - 查看指��数��的消息");
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

            handler.send("=== 最近 " + messages.size() + " ���消息������ ===");
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
                    // /hg ��名 成员名
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
                handler.send("/hg 群名 成员名         - 查看该群指定成员��最近20条消息");
                handler.send("/hg 群名 成员名 ���量    - 查看该群指定成员的指定数量消息");
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
                String sender = parts.length > 5 ? parts[5] : handler.username; // 新增：支持独��socket传递sender

                // 创建目标目录
                String basePath = isGroup ? "files/groups/" : "files/private/";
                String dirPath = basePath + (isGroup ? targetUser : sender + "_" + targetUser);
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
                        sender,          // String sender
                        targetUser,      // String receiver
                        isGroup,         // boolean isGroup
                        filePath        // String filePath
                );

                FileDatabase.addFile(fileInfo);

                // 发送文件通知
                String notifyMsg = String.format("FILE_NOTIFY:%s:%s:%d:%s:%s:%s",
                        fileId, fileName, fileSize, sender, targetUser, isGroup ? "group" : "private");

                if (isGroup) {
                    // 判断是否为小组（targetUser包含#）
                    if (targetUser.contains("#")) {
                        // 小组，targetUser格式为 群名#小组ID
                        String[] groupParts = targetUser.split("#", 2);
                        String groupName = groupParts[0];
                        String subGroupId = groupParts[1];
                        com.chat.model.Group group = com.chat.server.GroupDatabase.getGroup(groupName);
                        if (group != null) {
                            com.chat.NewFunctions.subgroup.SubGroup sg = group.getSubGroupById(subGroupId);
                            if (sg != null) {
                                for (String member : sg.getMembers()) {
                                    if (!member.equals(sender)) {
                                        ClientHandler memberHandler = com.chat.server.Server.getClientHandler(member);
                                        if (memberHandler != null) {
                                            memberHandler.send(notifyMsg);
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 群聊，通知所有群成员
                        Set<String> members = GroupDatabase.getGroupMembers(targetUser);
                        if (members != null) {
                            for (String member : members) {
                                if (!member.equals(sender)) {
                                    ClientHandler memberHandler = Server.getClientHandler(member);
                                    if (memberHandler != null) {
                                        memberHandler.send(notifyMsg);
                                    }
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
            // 关键：使用handler持有的统一输出���，并确保���程安全
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

                    // ���送文���数据
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
                        dos.writeUTF("ERROR:文件下���失败: " + e.getMessage());
                        dos.flush();
                    } catch (IOException ex) {
                        // ignore
                    }
                    System.out.println("文件下载处理异常: " + e.getMessage());
                }
            }
        }
    }
    /*public static class VoiceChatHandler implements MessageHandlerStrategy {
        private final VoiceChatManager voiceManager;
        private  Packetizer packetizer;
        private final Map<String, SessionContext> activeSessions;

        public VoiceChatHandler(VoiceChatManager voiceManager) {
            this.voiceManager = voiceManager;
            this.activeSessions = new ConcurrentHashMap<>();
        }

        @Override
        public void handle(String message, ClientHandler clientHandler) {
            String[] parts = message.split(" ");
            if (parts.length < 2) {
                clientHandler.send("SYSTEM: 无效语音命令格式");
                return;
            }

            String command = parts[0];
            String subCommand = parts[1];

            try {
                switch (command + " " + subCommand) {
                    case "/VOICE START":
                        handleStart(clientHandler, parts);
                        break;
                    case "/VOICE ACCEPT":
                        handleAccept(clientHandler, parts);
                        break;
                    case "/VOICE REJECT":
                        handleReject(clientHandler, parts);
                        break;
                    case "/VOICE END":
                        handleEnd(clientHandler, parts);
                        break;
                    case "/VOICE DATA":
                        handleData(clientHandler, parts);
                        break;
                    default:
                        clientHandler.send("SYSTEM: 未知语音命令");
                }
            } catch (Exception e) {
                clientHandler.send("SYSTEM: 语音处理错误: " + e.getMessage());
            }
        }

        private void handleStart(ClientHandler handler, String[] parts) {
            if (parts.length < 4) {
                handler.send("SYSTEM: 参数不足");
                return;
            }

            String sessionType = parts[2];
            String target = parts[3];
            String sessionId = generateSessionId(handler.username, target, sessionType);

            if (voiceManager.isSessionActive(sessionId)) {
                handler.send("SYSTEM: 会话已存在");
                return;
            }

            // 初始化语音会话
            try {
                voiceManager.initAudioDevices();
                voiceManager.startSession(sessionId);

                // 保存会话上下文
                activeSessions.put(sessionId, new SessionContext(
                        sessionType,
                        handler.username,
                        target
                ));

                // 发送邀请
                if ("PRIVATE".equals(sessionType)) {
                    sendPrivateInvite(handler, target, sessionId);
                } else {
                    sendGroupInvite(handler, target, sessionId);
                }
            } catch (Exception e) {
                handler.send("SYSTEM: 语音初始��失败: " + e.getMessage());
            }
        }
        private void handleReject(ClientHandler handler, String[] parts) {
            if (parts.length < 3) {
                handler.send("SYSTEM: 参数不足，格式应为: /VOICE REJECT [sessionId]");
                return;
            }

            String sessionId = parts[2];
            SessionContext context = activeSessions.get(sessionId);

            if (context == null) {
                handler.send("SYSTEM: 无效的会话ID");
                return;
            }

            // 通知发起方
            ClientHandler initiator = Server.getClientHandler(context.initiator);
            if (initiator != null) {
                initiator.send(String.format(
                        "/VOICE REJECTED %s %s",
                        handler.username,
                        sessionId
                ));
            }

            // 如果是私聊，直接结束会话
            if ("PRIVATE".equals(context.sessionType)) {
                voiceManager.stopSession(sessionId);
                activeSessions.remove(sessionId);
            }

            handler.send("SYSTEM: 已拒绝语音请求");
        }

        *//**
         * 在SessionContext类中添加状��跟踪
         *//*
        private static class SessionContext {
            final String sessionType;
            final String initiator;
            final String target;
            VoiceChatManager.SessionState state; // 使用VoiceChatManager中的枚举

            SessionContext(String sessionType, String initiator, String target) {
                this.sessionType = sessionType;
                this.initiator = initiator;
                this.target = target;
                this.state = VoiceChatManager.SessionState.PENDING;
            }
        }
        private void sendPrivateInvite(ClientHandler sender, String receiver, String sessionId) {
            ClientHandler target = Server.getClientHandler(receiver);
            if (target != null) {
                target.send(String.format(
                        "/VOICE INVITE PRIVATE %s %s",
                        sender.username,
                        sessionId
                ));
                sender.send("SYSTEM: 私聊邀请已发送");
            } else {
                sender.send("SYSTEM: 用户不在线");
            }
        }

        private void sendGroupInvite(ClientHandler sender, String groupId, String sessionId) {
            Set<String> members = GroupDatabase.getGroupMembers(groupId);
            if (members != null) {
                members.stream()
                        .filter(m -> !m.equals(sender.username))
                        .map(Server::getClientHandler)
                        .filter(Objects::nonNull)
                        .forEach(handler -> handler.send(String.format(
                                "/VOICE INVITE GROUP %s %s %s",
                                sender.username,
                                groupId,
                                sessionId
                        )));
                sender.send("SYSTEM: 群聊邀请已发送");
            }
        }

        private void handleAccept(ClientHandler handler, String[] parts) {
            if (parts.length < 3) {
                handler.send("SYSTEM: 参数不足");
                return;
            }

            String sessionId = parts[2];
            SessionContext context = activeSessions.get(sessionId);

            if (context == null) {
                handler.send("SYSTEM: 无效会话ID");
                return;
            }

            // 建立语音连接
            try {
                if ("PRIVATE".equals(context.sessionType)) {
                    setupPrivateConnection(handler, context, sessionId);
                } else {
                    setupGroupConnection(handler, context, sessionId);
                }
            } catch (Exception e) {
                handler.send("SYSTEM: 连接建立失败: " + e.getMessage());
            }
        }

        private void setupPrivateConnection(ClientHandler handler,
                                            SessionContext context,
                                            String sessionId) {
            // 通知发起方
            ClientHandler initiator = Server.getClientHandler(context.initiator);
            if (initiator != null) {
                initiator.send(String.format(
                        "/VOICE ACCEPTED %s %s",
                        handler.username,
                        sessionId
                ));
            }

            handler.send("SYSTEM: 私聊语音已连接");
        }

        private void setupGroupConnection(ClientHandler handler,
                                          SessionContext context,
                                          String sessionId) {
            try {
                // 1. 启动群聊会话
                voiceManager.startGroupSession(sessionId);

                // 2. 添加当前用户
                voiceManager.addParticipant(sessionId, handler.username);

                // 3. 更新状态
                context.state = VoiceChatManager.SessionState.ACTIVE;

                // 4. 通知群成员
                notifyGroupMembers(handler, context, sessionId);

            } catch (Exception e) {
                handler.send("SYSTEM: 加入群聊失败: " + e.getMessage());
                voiceManager.stopSession(sessionId);
            }
        }

        private void notifyGroupMembers(ClientHandler handler,
                                        SessionContext context,
                                        String sessionId) {
            Set<String> members = GroupDatabase.getGroupMembers(context.target);
            if (members != null) {
                String notification = String.format(
                        "/VOICE MEMBER_JOINED %s %s",
                        handler.username,
                        sessionId
                );

                members.stream()
                        .filter(m -> !m.equals(handler.username))
                        .map(Server::getClientHandler)
                        .filter(Objects::nonNull)
                        .forEach(client -> client.send(notification));
            }
        }
        private void handleData(ClientHandler handler, String[] parts) throws Exception {
            if (parts.length < 4) {
                return;
            }

            String sessionId = parts[2];
            String base64Data = parts[3];

            // Base64解码字符串到字节数组
            byte[] encodedBytes = Base64.getDecoder().decode(base64Data);

            // 使用AudioDecoder解码
            byte[] pcmData = voiceManager.encoder.decodeAudio(encodedBytes);
            // 通过VoiceManager处理音频数据
            voiceManager.receivePackets(sessionId,
                    Packetizer.depacketize(pcmData));
        }

        private void handleEnd(ClientHandler handler, String[] parts) {
            if (parts.length < 3) {
                handler.send("SYSTEM: 参数不足");
                return;
            }

            String sessionId = parts[2];
            voiceManager.stopSession(sessionId);
            activeSessions.remove(sessionId);

            // 通知其他参��者
            SessionContext context = activeSessions.get(sessionId);
            if (context != null) {
                notifyParticipants(sessionId, context, handler.username);
            }

            handler.send("SYSTEM: 语音会话已结束");
        }

        private void notifyParticipants(String sessionId,
                                        SessionContext context,
                                        String sender) {
            if ("PRIVATE".equals(context.sessionType)) {
                ClientHandler target = Server.getClientHandler(context.target);
                if (target != null) {
                    target.send(String.format(
                            "/VOICE ENDED %s %s",
                            sender,
                            sessionId
                    ));
                }
            } else {
                // 群聊通知逻辑
            }
        }

        private String generateSessionId(String user1, String user2, String type) {
            return type + "-" + user1 + "-" + user2 + "-" + System.currentTimeMillis();
        }


    }*/
    // 新增：图片接收与转发处理
    class ImageReceiveHandler implements MessageHandlerStrategy {
        @Override
        public void handle(String message, ClientHandler handler) {
            try {
                System.out.println("[IMAGE] 收到图片上传命令: " + message);
                // 解析命令：/IMAGE 文件名 文件大小 目标用户
                String[] parts = message.trim().split(" ", 4);
                if (parts.length < 4) {
                    System.err.println("[IMAGE] 图片命令格式错误: " + message);
                    handler.send("ERROR: 图片命令格式错误");
                    return;
                }
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                String targetUser = parts[3];
                // 保存图片到服务器本地
                String saveDir = "files/images/" + handler.username + "_to_" + targetUser;
                java.io.File dir = new java.io.File(saveDir);
                if (!dir.exists()) dir.mkdirs();
                String filePath = saveDir + "/" + fileName;
                java.io.File file = new java.io.File(filePath);
                FileOutputStream fos = new FileOutputStream(file);
                long remaining = fileSize;
                byte[] buffer = new byte[8192];
                while (remaining > 0) {
                    int read = handler.in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                    if (read == -1) break;
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
                fos.close();
                System.out.println("[IMAGE] 图片保存到: " + filePath);
                // 通知目标用户有图片
                ClientHandler targetHandler = Server.getClientHandler(targetUser);
                if (targetHandler != null) {
                    System.out.println("[IMAGE] 通知目���用户: " + targetUser + " IMAGE_NOTIFY:" + fileName + ":" + fileSize + ":" + handler.username + ":" + filePath);
                    targetHandler.send("IMAGE_NOTIFY:" + fileName + ":" + fileSize + ":" + handler.username + ":" + filePath);
                } else {
                    System.out.println("[IMAGE] 目标用户 " + targetUser + " 不在线，无法通知");
                }
                handler.send("SUCCESS: 图片发送成功");
            } catch (Exception e) {
                System.err.println("[IMAGE] 图片接收失败: " + e.getMessage());
                handler.send("ERROR: 图片接收失败: " + e.getMessage());
            }
        }
    }
    // 图片专用socket处理线程
    public static class ImageSocketHandler extends Thread {
        private final Socket imageSocket;
        public ImageSocketHandler(Socket socket) {
            this.imageSocket = socket;
        }
        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(imageSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(imageSocket.getOutputStream())) {
                // 1. 读取命令和元数据
                String meta = in.readUTF(); // 可能是/IMAGE或/IMAGE_DOWNLOAD
                System.out.println("[ImageSocket] 收到命令: " + meta);
                if (meta.startsWith("/IMAGE_DOWNLOAD")) {
                    // 新协议：/IMAGE_DOWNLOAD 文件名 发送者 接收者
                    String[] parts = meta.trim().split(" ", 4);
                    if (parts.length < 4) {
                        System.err.println("[ImageSocket] 图片下载命令格式错误: " + meta);
                        out.writeUTF("ERROR: 图��下载命令格式错误");
                        return;
                    }
                    String fileName = parts[1];
                    String sender = parts[2];
                    String receiver = parts[3];
                    String filePath = "files/images/" + sender + "_to_" + receiver + "/" + fileName;
                    File file = new File(filePath);
                    if (!file.exists()) {
                        System.err.println("[ImageSocket] 图片文件不存在: " + filePath);
                        out.writeUTF("ERROR: 图片文件不存在");
                        return;
                    }
                    out.writeUTF("IMAGE_DATA");
                    out.writeUTF(fileName);
                    out.writeLong(file.length());
                    System.out.println("[ImageSocket] 开始发送图片数据: " + filePath);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = fis.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        out.flush();
                    }
                    System.out.println("[ImageSocket] 图片数据发送完成: " + filePath);
                    return;
                }
                // 原有图片上传逻辑
                String[] parts = meta.trim().split(" ", 5);
                if (parts.length < 5) {
                    System.err.println("[ImageSocket] 图片命令格式错误: " + meta);
                    out.writeUTF("ERROR: 图片命令格式错误");
                    return;
                }
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                String targetUser = parts[3];
                String sender = parts[4];
                boolean isGroup = sender.endsWith(":group");
                String realSender = isGroup ? sender.substring(0, sender.length() - 6) : sender;
                String saveDir = isGroup ? ("files/images/group_" + targetUser) : ("files/images/" + realSender + "_to_" + targetUser);
                File dir = new File(saveDir);
                if (!dir.exists()) dir.mkdirs();
                String filePath = saveDir + "/" + fileName;
                File file = new File(filePath);
                FileOutputStream fos = new FileOutputStream(file);
                long remaining = fileSize;
                byte[] buffer = new byte[8192];
                while (remaining > 0) {
                    int read = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                    if (read == -1) break;
                    fos.write(buffer, 0, read);
                    remaining -= read;
                }
                fos.close();
                System.out.println("[ImageSocket] 图片保��到: " + filePath);
                if (isGroup) {
                    Set<String> members = com.chat.server.GroupDatabase.getGroupMembers(targetUser);
                    if (members != null) {
                        for (String member : members) {
                            if (!member.equals(realSender)) {
                                ClientHandler targetHandler = com.chat.server.Server.getClientHandler(member);
                                if (targetHandler != null) {
                                    System.out.println("[ImageSocket] 通知群成员: " + member + " IMAGE_NOTIFY:" + fileName + ":" + fileSize + ":" + realSender + ":" + filePath + ":group");
                                    targetHandler.send("IMAGE_NOTIFY:" + fileName + ":" + fileSize + ":" + realSender + ":" + filePath + ":group");
                                }
                            }
                        }
                    }
                } else {
                    ClientHandler targetHandler = com.chat.server.Server.getClientHandler(targetUser);
                    if (targetHandler != null) {
                        System.out.println("[ImageSocket] 通知目标用户: " + targetUser + " IMAGE_NOTIFY:" + fileName + ":" + fileSize + ":" + realSender + ":" + filePath);
                        targetHandler.send("IMAGE_NOTIFY:" + fileName + ":" + fileSize + ":" + realSender + ":" + filePath);
                    } else {
                        System.out.println("[ImageSocket] 目标用户 " + targetUser + " 不在线，无法通知");
                    }
                }
                out.writeUTF("SUCCESS: 图片发送成功");
            } catch (Exception e) {
                System.err.println("[ImageSocket] 图片专用socket处理异常: " + e.getMessage());
            } finally {
                try { imageSocket.close(); } catch (IOException ignored) {}
            }
        }
    }
    // ================== 小组相关命令处理器 ==================
    class SubGroupCreateHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 3) {
                handler.send("ERROR: 格式为 /sg_create 群名 小组名");
                return;
            }
            String groupName = parts[1];
            String subGroupName = parts[2];
            var group = com.chat.server.GroupDatabase.getGroup(groupName);
            if (group == null) {
                handler.send("ERROR: 群聊不存在");
                return;
            }
            // 检查创建人是否已在任何小组
            if (com.chat.NewFunctions.subgroup.SubGroupManager.getUserSubGroup(group, username) != null) {
                handler.send("ERROR: 你已在该群的某个小组，不能重复创建");
                return;
            }
            // 创建小组，仅包含自己
            java.util.Set<String> members = new java.util.HashSet<>();
            members.add(username);
            StringBuilder errorMsg = new StringBuilder();
            com.chat.NewFunctions.subgroup.SubGroup sg = com.chat.NewFunctions.subgroup.SubGroupManager.createSubGroup(group, subGroupName, members, errorMsg);
            if (sg == null) {
                handler.send("ERROR: 小组创建失败 - " + errorMsg.toString());
                return;
            }
            handler.send("SUCCESS: 小组创建成功，ID=" + sg.getId());
            // 通知自己刷新小组列表
            ClientHandler selfHandler = com.chat.server.Server.getClientHandler(username);
            if (selfHandler != null) {
                selfHandler.send("SG_LIST:" + com.chat.NewFunctions.subgroup.SubGroupManager.buildSubGroupListString(group));
            }
        }
    }

    static class SubGroupInviteHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            System.out.println("[DEBUG][SubGroupInviteHandler] 收到邀请命令: " + message);
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 4) {
                System.out.println("[DEBUG][SubGroupInviteHandler] 参数不足: " + Arrays.toString(parts));
                handler.send("ERROR: 格式为 /sg_invite 群名 小组ID 用户名");
                return;
            }
            String groupName = parts[1];
            String subGroupId = parts[2];
            String inviteUser = parts[3];
            System.out.println("[DEBUG][SubGroupInviteHandler] groupName=" + groupName + ", subGroupId=" + subGroupId + ", inviteUser=" + inviteUser);
            var group = com.chat.server.GroupDatabase.getGroup(groupName);
            if (group == null) {
                System.out.println("[DEBUG][SubGroupInviteHandler] 群聊不存在: " + groupName);
                handler.send("ERROR: 群聊不存在");
                return;
            }
            // 只能邀请本群成员
            if (!group.getMemberNames().contains(inviteUser)) {
                System.out.println("[DEBUG][SubGroupInviteHandler] 只能邀请本群成员: " + inviteUser);
                handler.send("ERROR: 只能邀请本群成员");
                return;
            }
            // 被邀请人不能已在任何小组
            if (com.chat.NewFunctions.subgroup.SubGroupManager.getUserSubGroup(group, inviteUser) != null) {
                System.out.println("[DEBUG][SubGroupInviteHandler] 用户已在本群其他小组: " + inviteUser);
                handler.send("ERROR: 用户已在本群其他小组，不能被邀请");
                return;
            }
            boolean ok = com.chat.NewFunctions.subgroup.SubGroupManager.inviteToSubGroup(group, subGroupId, inviteUser);
            System.out.println("[DEBUG][SubGroupInviteHandler] inviteToSubGroup result: " + ok);
            if (ok) {
                handler.send("SUCCESS: 邀请成功");
                // 通知被邀请用户
                ClientHandler target = com.chat.server.Server.getClientHandler(inviteUser);
                if (target != null) {
                    System.out.println("[DEBUG][SubGroupInviteHandler] 通知被邀请用户: " + inviteUser);
                    // 新增：发送专用小组邀请消息，带上群名、小组ID、邀请人
                    target.send("SG_INVITE_NOTIFY:" + groupName + ":" + subGroupId + ":" + handler.username);
                } else {
                    System.out.println("[DEBUG][SubGroupInviteHandler] 被邀请用户不在线: " + inviteUser);
                }
            } else {
                handler.send("ERROR: 邀请失败（用户已在其他小组或参数有误）");
            }
        }
    }

    // 新增：小组邀请接受命令处理器
    class SubGroupAcceptHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 3) {
                handler.send("ERROR: 格式为 /sg_accept 群名 小组ID");
                return;
            }
            String groupName = parts[1];
            String subGroupId = parts[2];
            var group = com.chat.server.GroupDatabase.getGroup(groupName);
            if (group == null) {
                handler.send("ERROR: 群聊不存在");
                return;
            }
            boolean ok = com.chat.NewFunctions.subgroup.SubGroupManager.acceptInviteToSubGroup(group, subGroupId, handler.username);
            if (ok) {
                handler.send("SUCCESS: 已加入小组");
                // 通知所有小组成员刷新小组列表
                for (var sg : group.getSubGroups()) {
                    for (String member : sg.getMembers()) {
                        ClientHandler ch = com.chat.server.Server.getClientHandler(member);
                        if (ch != null) {
                            ch.send("SG_LIST:" + com.chat.NewFunctions.subgroup.SubGroupManager.buildSubGroupListString(group));
                        }
                    }
                }
            } else {
                handler.send("ERROR: 接受邀请失败（可能未被邀请或参数有误）");
            }
        }
    }
    class SubGroupJoinHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 3) {
                handler.send("ERROR: 格式为 /sg_join 群 小组ID");
                return;
            }
            String groupName = parts[1];
            String subGroupId = parts[2];
            var group = com.chat.server.GroupDatabase.getGroup(groupName);
            if (group == null) {
                handler.send("ERROR: 群聊不存在");
                return;
            }
            // 自动退出原有小组，加入新小组
            boolean ok = com.chat.NewFunctions.subgroup.SubGroupManager.joinSubGroup(group, subGroupId, username);
            if (ok) {
                handler.send("SUCCESS: 加入小组成功");
            } else {
                handler.send("ERROR: 加入小组失败（可能已在其他小组或参数有误）");
            }
        }
    }
    class SubGroupExitHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 2) {
                handler.send("ERROR: 格式为 /sg_exit 群名");
                return;
            }
            String groupName = parts[1];
            var group = com.chat.server.GroupDatabase.getGroup(groupName);
            if (group == null) {
                handler.send("ERROR: 群聊不存在");
                return;
            }
            boolean ok = com.chat.NewFunctions.subgroup.SubGroupManager.exitSubGroup(group, username);
            if (ok) {
                handler.send("SUCCESS: 退出小组成功");
            } else {
                handler.send("ERROR: 退出小组失败（可能未加入任何小组）");
            }
        }
    }
    class SubGroupMsgHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+", 4);
            if (parts.length < 4) {
                handler.send("ERROR: 格式为 /sg_msg 群名 小组ID 消息内容");
                return;
            }
            String groupName = parts[1];
            String subGroupId = parts[2];
            String msg = parts[3];
            var group = com.chat.server.GroupDatabase.getGroup(groupName);
            if (group == null) {
                handler.send("ERROR: 群聊不存在");
                return;
            }
            var sg = group.getSubGroupById(subGroupId);
            if (sg == null) {
                handler.send("ERROR: 小组不存在");
                return;
            }
            if (!sg.contains(username)) {
                handler.send("ERROR: 你不在该小组中");
                return;
            }
            // 存储小组消���
            com.chat.server.MessageStorage.saveMessage(username, subGroupId, msg, com.chat.server.MessageStorage.MessageType.SUBGROUP, groupName, subGroupId);
            // 只发给小组成员
            for (String member : sg.getMembers()) {
                com.chat.server.ClientHandler target = com.chat.server.Server.getClientHandler(member);
                if (target != null) {
                    target.send("[小组] " + groupName + "|" + sg.getName() + "|" + username + ": " + msg);
                }
            }
            // handler.send("SUCCESS: 小组消息已发送"); // 不再单独回显，直接靠消息分发
        }
    }
    // 新增：小组历史消息查询处理器
    class SubGroupHistoryHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 3) {
                handler.send("ERROR: 格式为 /sg_history 群名 小组ID [数量]");
                return;
            }
            String groupName = parts[1];
            String subGroupId = parts[2];
            int limit = 20;
            if (parts.length >= 4) {
                try { limit = Integer.parseInt(parts[3]); } catch (Exception ignore) {}
            }
            var msgs = com.chat.server.MessageStorage.getSubGroupMessages(groupName, subGroupId, limit);
            if (msgs.isEmpty()) {
                handler.send("=== 小组 " + groupName + " 的小组ID=" + subGroupId + " 暂无消息记录 ===");
                return;
            }
            handler.send("=== 小组 " + groupName + " 的小组ID=" + subGroupId + " 的消息记录 (" + msgs.size() + " 条) ===");
            for (var msg : msgs) {
                handler.send(msg.getSender() + ": " + msg.getContent());
            }
        }
    }
    class SubGroupListHandler implements MessageHandlerStrategy {
        @Override
        public void handle(String message, ClientHandler handler) {
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 2) {
                handler.send("ERROR: 格式为 /sg_list 群名");
                return;
            }
            String groupName = parts[1];
            var group = com.chat.server.GroupDatabase.getGroup(groupName);
            if (group == null) {
                handler.send("ERROR: 群聊不存在");
                return;
            }
            // 构建小组列表字符串
            String sgList = com.chat.NewFunctions.subgroup.SubGroupManager.buildSubGroupListString(group);
            handler.send("SG_LIST:" + sgList);
        }
    }    // ================== 小组相关命令处理器 END ==================
    // ================== 语音专用socket处理线程 ==================
    public static class VoiceSocketHandler extends Thread {
        private final Socket voiceSocket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private String username;
        private volatile boolean running = true;

        public VoiceSocketHandler(Socket socket) {
            this.voiceSocket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(voiceSocket.getOutputStream());
                in = new ObjectInputStream(voiceSocket.getInputStream());
                // 第一个包要求客户端先发送用户名
                Object first = in.readObject();
                if (!(first instanceof String)) {
                    voiceSocket.close();
                    return;
                }
                username = (String) first;
                Server.registerVoiceClient(username, this);
                System.out.println("[VoiceSocket] 用户 " + username + " 语音socket已注册");
                while (running && !voiceSocket.isClosed()) {
                    // 读取sessionId和音频包
                    String sessionId = (String) in.readObject();
                    List<AudioPacket> packets = (List<AudioPacket>) in.readObject();
                    // 解析目标用户
                    String targetUser = parseTargetUserFromSessionId(sessionId, username);
                    if (targetUser == null) continue;
                    VoiceSocketHandler targetHandler = Server.getVoiceClient(targetUser);
                    if (targetHandler != null) {
                        targetHandler.sendAudio(sessionId, packets);
                    }
                }
            } catch (Exception e) {
                System.out.println("[VoiceSocket] " + username + " 语音socket断开: " + e.getMessage());
            } finally {
                running = false;
                if (username != null) Server.unregisterVoiceClient(username);
                try { voiceSocket.close(); } catch (IOException ignored) {}
            }
        }
        public void sendAudio(String sessionId, List<AudioPacket> packets) {
            synchronized (out) {
                try {
                    out.writeObject(sessionId);
                    out.writeObject(packets);
                    out.flush();
                } catch (IOException e) {
                    System.out.println("[VoiceSocket] 向 " + username + " 发送音频失败: " + e.getMessage());
                }
            }
        }
        private String parseTargetUserFromSessionId(String sessionId, String sender) {
            // 假设 sessionId 格式为 userA_to_userB
            if (sessionId == null) return null;
            String[] parts = sessionId.split("_to_");
            if (parts.length == 2) {
                if (parts[0].equals(sender)) return parts[1];
                else return parts[0];
            }
            return null;
        }
    }
    // ================== 语音通话邀请/同意处理器 ==================
    class VoiceInviteHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            // /voice_invite 目标用户名
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 2) {
                handler.send("ERROR: 格式为 /voice_invite 用户名");
                return;
            }
            String targetUser = parts[1];
            ClientHandler targetHandler = Server.getClientHandler(targetUser);
            if (targetHandler != null) {
                // 通知目标用户有语音通话邀请
                targetHandler.send("VOICE_INVITE_FROM:" + handler.username);
                handler.send("SUCCESS: 语音通话邀请已发送");
            } else {
                handler.send("ERROR: 目标用户不在线");
            }
        }
    }
    class VoiceAcceptHandler implements MessageHandlerStrategy {
        public void handle(String message, ClientHandler handler) {
            // /voice_accept 发起方用户名
            String[] parts = message.trim().split("\\s+");
            if (parts.length < 2) {
                handler.send("ERROR: 格式为 /voice_accept 用户名");
                return;
            }
            String inviter = parts[1];
            ClientHandler inviterHandler = Server.getClientHandler(inviter);
            if (inviterHandler != null) {
                // 通知发起方，对方已同意
                inviterHandler.send("VOICE_ACCEPTED_BY:" + handler.username);
                handler.send("SUCCESS: 已同意语音通话");
            } else {
                handler.send("ERROR: 发起方不在线");
            }
        }
    }
}
