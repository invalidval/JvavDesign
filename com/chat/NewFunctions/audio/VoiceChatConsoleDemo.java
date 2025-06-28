package com.chat.NewFunctions.audio;

import javax.sound.sampled.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class VoiceChatConsoleDemo {
    private static final int PORT = 20000;
    private static final int AUDIO_BUFFER_SIZE = 4096;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(44100, 16, 2, true, false);
    private static TargetDataLine microphone;
    private static SourceDataLine speaker;
    private static AudioEncoder encoder = new AudioEncoder();
    private static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("请选择模式: 1-主叫 2-被叫");
        int mode = scanner.nextInt();
        scanner.nextLine();
        if (mode == 1) {
            System.out.print("请输入对方IP地址: ");
            String ip = scanner.nextLine().trim();
            runAsCaller(ip);
        } else {
            runAsReceiver();
        }
    }

    private static void runAsCaller(String ip) throws Exception {
        initAudio();
        try (Socket socket = new Socket(ip, PORT)) {
            System.out.println("已连接到对方: " + ip + ":" + PORT);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            Thread sendThread = new Thread(() -> sendAudio(out));
            Thread recvThread = new Thread(() -> receiveAudio(in));
            sendThread.start();
            recvThread.start();
            System.out.println("按回车键挂断...");
            System.in.read();
            running = false;
            sendThread.join();
            recvThread.join();
        }
        closeAudio();
    }

    private static void runAsReceiver() throws Exception {
        initAudio();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("等待对方连接，端口: " + PORT);
            try (Socket socket = serverSocket.accept()) {
                System.out.println("对方已连接: " + socket.getInetAddress());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                Thread sendThread = new Thread(() -> sendAudio(out));
                Thread recvThread = new Thread(() -> receiveAudio(in));
                sendThread.start();
                recvThread.start();
                System.out.println("按回车键挂断...");
                System.in.read();
                running = false;
                sendThread.join();
                recvThread.join();
            }
        }
        closeAudio();
    }

    private static void initAudio() throws LineUnavailableException {
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
        microphone.open(AUDIO_FORMAT);
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
        speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        speaker.open(AUDIO_FORMAT);
    }

    private static void closeAudio() {
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        if (speaker != null) {
            speaker.stop();
            speaker.close();
        }
    }

    private static void sendAudio(DataOutputStream out) {
        try {
            microphone.start();
            byte[] buffer = new byte[AUDIO_BUFFER_SIZE];
            while (running) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    byte[] raw = Arrays.copyOf(buffer, bytesRead);
                    byte[] encoded = encoder.encodeAudio(raw);
                    out.writeInt(encoded.length);
                    out.write(encoded);
                    out.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("发送音频失败: " + e.getMessage());
        }
    }

    private static void receiveAudio(DataInputStream in) {
        try {
            speaker.start();
            while (running) {
                int len = in.readInt();
                if (len <= 0) continue;
                byte[] encoded = new byte[len];
                in.readFully(encoded);
                byte[] decoded = encoder.decodeAudio(encoded);
                speaker.write(decoded, 0, decoded.length);
            }
        } catch (Exception e) {
            System.err.println("接收音频失败: " + e.getMessage());
        }
    }
}

