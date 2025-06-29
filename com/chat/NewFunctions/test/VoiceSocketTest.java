package com.chat.NewFunctions.test;

import java.io.*;
import java.net.*;
import java.util.*;

public class VoiceSocketTest {
    // Mock AudioPacket 类（如实际有请替换导入）
    public static class AudioPacket implements Serializable {
        public int id;
        public byte[] data;
        public AudioPacket(int id, byte[] data) {
            this.id = id;
            this.data = data;
        }
        public String toString() {
            return "AudioPacket{id=" + id + ", data.length=" + (data == null ? 0 : data.length) + "}";
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 19999;
        // 启动服务端线程
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("[Server] Listening on port " + port);
                Socket client = serverSocket.accept();
                System.out.println("[Server] Client connected: " + client.getInetAddress());
                ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                while (true) {
                    Object sessionId = in.readObject();
                    Object packets = in.readObject();
                    System.out.println("[Server] Received sessionId: " + sessionId);
                    System.out.println("[Server] Received packets: " + packets);
                }
            } catch (Exception e) {
                System.err.println("[Server] Exception: " + e.getMessage());
            }
        }).start();

        // 等待服务端启动
        Thread.sleep(500);

        // 启动客户端线程
        new Thread(() -> {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                System.out.println("[Client] Connected to server");
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                String sessionId = "1_to_3";
                List<AudioPacket> packets = Arrays.asList(
                        new AudioPacket(1, new byte[]{1,2,3}),
                        new AudioPacket(2, new byte[]{4,5,6})
                );
                out.writeObject(sessionId);
                out.writeObject(packets);
                out.flush();
                System.out.println("[Client] Sent sessionId and packets");
            } catch (Exception e) {
                System.err.println("[Client] Exception: " + e.getMessage());
            }
        }).start();
    }
}

