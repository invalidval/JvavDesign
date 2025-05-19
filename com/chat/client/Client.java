package com.chat.client;

import java.io.*;
import java.net.Socket;

public class Client {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

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

    public static void main(String[] args) {
        try {
            Client client = new Client("localhost", 8888);
            client.sendMessage("LOGIN:用户名");
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));

            System.out.println("连接到服务器成功！请输入消息：");

            // 启动接收消息线程
            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = client.receiveMessage()) != null) {
                        System.out.println("服务器: " + serverMessage);
                    }
                } catch (IOException e) {
                    System.out.println("与服务器断开连接。");
                }
            }).start();

            // 主线程发送消息
            String userInput;
            while ((userInput = consoleInput.readLine()) != null) {
                client.sendMessage(userInput);
            }

        } catch (IOException e) {
            System.out.println("无法连接到服务器：" + e.getMessage());
        }
    }
}
