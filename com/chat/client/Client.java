package com.chat.client;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class Client implements MessageSubject {
    private Socket socket;
    private DataInputStream in; // 使用DataInputStream
    private DataOutputStream out; // 使用DataOutputStream
    private List<MessageObserver> observers = new CopyOnWriteArrayList<>();
    private volatile boolean listening = false;

    private Thread listenThread;
    private static final List<Client> allClients = new CopyOnWriteArrayList<>();

    public Client(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        // 使用DataInputStream和DataOutputStream
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        allClients.add(this);
    }

    public Socket getSocket() {
        return socket;
    }

    // 新增：提供对输入流的访问
    public DataInputStream getDataInputStream() {
        return in;
    }

    // 新增：提供对输出流的访问
    public DataOutputStream getDataOutputStream() {
        return out;
    }

    public void sendMessage(String message) {
        try {
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            // 在实际应用中，这里应该有更完善的异常处理
            e.printStackTrace();
        }
    }

    public String receiveMessage() throws IOException {
        return in.readUTF(); // 使用readUTF
    }


    public void addObserver(MessageObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(MessageObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(String message) {
        for (MessageObserver observer : observers) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                observer.onMessageReceived(message);
            });
        }
    }

    // 启动异步消息监听线程
    public void startListening() {
        if (listening) return;
        listening = true;
        listenThread = new Thread(() -> {
            try {
                while (listening) {
                    String serverMessage = receiveMessage();
                    if (serverMessage == null) break;
                    final String msg = serverMessage;
                    notifyObservers(msg);
                }
            } catch (IOException e) {
                // 可在UI层处理断开
            }
        });
        listenThread.setDaemon(true);
        listenThread.start();
    }

    // 新增：通过socket查找Client实例
    public static Client getClientBySocket(Socket socket) {
        for (Client c : allClients) {
            if (c.socket == socket) return c;
        }
        return null;
    }

    public void stopListening() {
        listening = false;
        try {
            if (socket != null && !socket.isClosed()) {
                // 关闭流会连锁关闭socket
                if (out != null) out.close();
                if (in != null) in.close();
                socket.close();
            }
        } catch (IOException e) {
            // 处理关闭套接字时的异常
            e.printStackTrace();
        }
    }
}

interface MessageSubject {
    void addObserver(MessageObserver observer);
    void removeObserver(MessageObserver observer);
    void notifyObservers(String message);
}