package com.chat.server;

import java.io.*;
import java.net.*;
import java.util.Properties;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 8888;
    private static ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static ExecutorService threadPool;

    static {
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream("config/threadpool.properties"));
            int corePoolSize = Integer.parseInt(properties.getProperty("corePoolSize"));
            int maximumPoolSize = Integer.parseInt(properties.getProperty("maximumPoolSize"));
            long keepAliveTime = Long.parseLong(properties.getProperty("keepAliveTime"));
            threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        } catch (IOException e) {
            System.out.println("加载线程池配置失败，使用默认配置");
            threadPool = Executors.newFixedThreadPool(10);
        }
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("服务器已启动，等待客户端连接...");
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

    public static void addClient(String name, ClientHandler handler) {
        clients.put(name, handler);
    }

    public static void removeClient(String name) {
        clients.remove(name);
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
}
