package com.chat.server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void run() {
        try {
            // 读取用户名
            username = in.readLine().split(":")[1];
            Server.addClient(username, this);
            System.out.println(username + " 已连接");

            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("PRIVATE:")) {
                    String[] parts = message.split(":", 3);
                    String targetUser = parts[1];
                    String privateMessage = parts[2];
                    Server.sendPrivateMessage(username, targetUser, privateMessage);
                } else {
                    System.out.println(username + ": " + message);
                    Server.broadcast(username + ": " + message);
                }
            }
        } catch (IOException e) {
            System.out.println(username + " 已断开连接");
        } finally {
            Server.removeClient(username);
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
}
