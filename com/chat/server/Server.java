package com.chat.server;

import java.io.*;
import java.net.*;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;
import com.chat.server.UserDatabase;
import com.chat.server.GroupDatabase;
import com.chat.server.MessageStorage;
import com.chat.model.User;

public class Server implements UserDatabase.UserDatabaseObserver {
    private static final int PORT = 8888;
    private static ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static ExecutorService threadPool;
    private static Server serverInstance = new Server();

    static {
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream("config/threadpool.properties"));
            int corePoolSize = Integer.parseInt(properties.getProperty("corePoolSize"));
            int maximumPoolSize = Integer.parseInt(properties.getProperty("maximumPoolSize"));
            long keepAliveTime = Long.parseLong(properties.getProperty("keepAliveTime"));
            threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>());
        } catch (IOException e) {
            System.out.println("加载线程池配置失败，使用默认配置");
            threadPool = Executors.newFixedThreadPool(10);
        }

        UserDatabase.initialize(); // 初始化用户数据库
        MessageStorage.initialize(); // 初始化消息存储系统
        GroupDatabase.initialize(); // 初始化群聊数据库
        UserDatabase.addObserver(serverInstance); // 注册服务器为观察者
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("服务器已启动，等待客户端连接...");
        // 创建文件存储目录
        new File("files/groups/").mkdirs();
        new File("files/private/").mkdirs();
        while (true) {
            Socket socket = serverSocket.accept();
            threadPool.execute(new ClientHandler(socket));
        }
    }

    public static void broadcast(String msg) {
        for (ClientHandler handler : clients.values()) {
            handler.send(msg);
        }
    }

    /**
     * 添加新的客户端连接，若已存在旧连接则强制踢掉
     */
    public static void addClient(String name, ClientHandler handler) {
        ClientHandler existingHandler = clients.get(name);

        if (existingHandler != null && existingHandler != handler) {
            System.out.println("【addClient】发现旧连接，准备踢出: " + existingHandler);
            existingHandler.send("SYSTEM: 您的账号在另一台设备上登录，当前连接将被断开。[000]");
            existingHandler.forceDisconnect(); // 会关闭 socket，使旧连接进入 finally 并 remove
        } else {
            System.out.println("【addClient】无旧连接或已是最新连接");
        }

        clients.put(name, handler);
        System.out.println("【addClient】最终 clients 状态: " + clients.keySet());
    }

    /**
     * 移除客户端连接
     */
    public static void removeClient(String name) {
        ClientHandler removed = clients.remove(name);
        if (removed != null) {
            UserDatabase.logoutUser(name);
            System.out.println("【removeClient】移除用户: " + name + ", 剩余 clients: " + clients.keySet());
        } else {
            System.out.println("【removeClient】用户不存在或已移除: " + name);
        }
    }

    /**
     * 获取当前连接的 handler
     */
    public static ClientHandler getClient(String name) {
        return clients.get(name);
    }

    public static void sendPrivateMessage(String sender, String receiver, String message) {
        ClientHandler receiverHandler = clients.get(receiver);
        if (receiverHandler != null) {
            receiverHandler.send("[私聊] " + sender + ": " + message);
        } else {
            ClientHandler senderHandler = clients.get(sender);
            if (senderHandler != null) {
                senderHandler.send("用户 " + receiver + " 不在线或不存在。");
            }
        }
    }

    public static ClientHandler getClientHandler(String name) {
        return clients.get(name);
    }

    // 群聊消息分发
    public static void sendGroupMessage(String groupName, String sender, String message) {
        Set<String> members = GroupDatabase.getGroupMembers(groupName);
        if (members == null)
            return;
        for (String member : members) {
            ClientHandler handler = clients.get(member);
            if (handler != null) {
                handler.send("[群聊] " + groupName + "|" + sender + ": " + message);
            }
        }
    }

    // 可扩展的消息分发接口
    public interface MessageDispatcher {
        void dispatch(String msg);
    }

    // 实现UserDatabaseObserver接口
    @Override
    public void onUserChanged(User user) {
        // 可以在这里处理用户状态变化的逻辑
        System.out.println("用户状态变化: " + user.getName() + " - 在线: " + user.isOnline());
    }

}
