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
    private volatile boolean listening = false;

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
        listening = false;
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

    // 启动异步消息监听线程
    public void startListening() {
        if (listening) return;
        listening = true;
        Thread t = new Thread(() -> {
            try {
                String serverMessage;
                while (listening && (serverMessage = receiveMessage()) != null) {
                    final String msg = serverMessage;
                    for (MessageObserver observer : observers) {
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            observer.onMessageReceived(msg);

                        });
                    }
                }
            } catch (IOException e) {
                // 可在UI层处理断开
            }
        });
        t.setDaemon(true);
        t.start();
    }
}

interface MessageSubject {
    void addObserver(MessageObserver observer);
    void removeObserver(MessageObserver observer);
    void notifyObservers(String message);
}

