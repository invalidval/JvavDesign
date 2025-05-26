package com.chat.client;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Client implements MessageSubject {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private List<MessageObserver> observers = new ArrayList<>();

    public Client(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String receiveMessage() throws IOException {
        return in.readLine();
    }

    public void close() throws IOException {
        socket.close();
    }

    public void addObserver(MessageObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(MessageObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String message) {
        for (MessageObserver observer : observers) {
            observer.onMessageReceived(message);
        }
    }

    public static void main(String[] args) {
        try {
            Client client = new Client("localhost", 8888);
//            client.sendMessage("LOGIN:用户名");
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));

            // 控制台消息观察者
            client.addObserver(new ConsoleMessageObserver());

//            System.out.println("> Loaded");
//            System.out.println("使用说明：");
//            System.out.println("/r 用户名 密码    —— 注册");
//            System.out.println("/l 用户名 密码    —— 登录");
//            System.out.println("/a 好友用户名     —— 添加好友");
//            System.out.println("/f           —— 查看好友列表");
//            System.out.println("/p 用户名 消息内容 —— 私聊");
//            System.out.println("直接输入内容为群聊消息");

            boolean loggedIn = false;

            // 主线程处理用户输入
            while (true) {
                String userInput = consoleInput.readLine();
                if (!loggedIn) {
                    if (userInput.startsWith("/r") || userInput.startsWith("/l")) {
                        client.sendMessage(userInput);
                        String serverResponse = client.receiveMessage();
                        if (serverResponse == null) {
                            System.out.println("服务器已断开连接，请重新连接。");
                            break;
                        }
                        System.out.println(serverResponse);
                        if (serverResponse.startsWith("SUCCESS: 登录成功")) {
                            loggedIn = true;

                            // 启动接收消息线程
                            new Thread(() -> {
                                try {
                                    String serverMessage;
                                    while ((serverMessage = client.receiveMessage()) != null) {
                                        client.notifyObservers(serverMessage);
                                    }
                                } catch (IOException e) {
                                    System.out.println("与服务器断开连接。");
                                }
                            }).start();
                        }
                    } else {
                        System.out.println("请先注册或登录！");
                    }
                } else {
                    client.sendMessage(userInput);
                }
            }

        } catch (IOException e) {
            System.out.println("无法连接到服务器：" + e.getMessage());
        }
    }
}

// 观察者模式接口
interface MessageObserver {
    void onMessageReceived(String message);
}

interface MessageSubject {
    void addObserver(MessageObserver observer);
    void removeObserver(MessageObserver observer);
    void notifyObservers(String message);
}

// 控制台消息观察者实现
class ConsoleMessageObserver implements MessageObserver {
    @Override
    public void onMessageReceived(String message) {
        System.out.println("服务器: " + message);
    }
}

