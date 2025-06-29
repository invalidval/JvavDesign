package com.chat.NewFunctions.audio;

import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.*;
import java.net.*;

/**
 * 语音聊天管理器 - 增强版
 * 架构：客户端向服务器发送多个目标主机请求，获得相应的ip和端口进程，然后在多个客户端之间建立TCP连接，实现语音通信
 * 功能：
 * 1. 管理语音会话的生命周期
 * 2. 处理音频数据的编码/发送/接收/解码流程
 * 3. 支持多方会议功能
 * 4. 提供音频设备管理
 */
public class VoiceChatManager {
    // 核心组件
    private final Packetizer packetizer;
    public  final AudioEncoder encoder;
    private final ExecutorService executorService;

    // 会议管理
    private final Map<String, List<AudioPacket>> conferenceSessions;
    private final Set<String> activeSessions;
    public enum SessionState {
        PENDING, ACTIVE, ENDED
    }

    // 添加群聊会话管理
    private final Map<String, GroupSession> activeGroupSessions = new ConcurrentHashMap<>();


    // 音频设备
    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private final AtomicBoolean isRunning;

    // 配置参数
    private static final int THREAD_POOL_SIZE = 5;
    private static final int AUDIO_BUFFER_SIZE = 4096;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            44100, 16, 2, true, false);

    // Socket通信相关
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Thread receiveThread;

    // 新增：语音独立Socket参数
    private String voiceHost;
    private int voicePort;

    // 新增：音频专用持久Socket及流
    private Socket voiceSocket;
    private ObjectOutputStream voiceOut;
    private ObjectInputStream voiceIn;
    private Thread voiceReceiveThread;

    public VoiceChatManager() {
        this.packetizer = new Packetizer();
        this.encoder = new AudioEncoder();
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.conferenceSessions = new ConcurrentHashMap<>();
        this.activeSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.isRunning = new AtomicBoolean(false);
    }

    /**
     * 初始化音频设备
     */


    // 添加缺失的方法
    public List<String> getSessionParticipants(String sessionId) {
        GroupSession session = activeGroupSessions.get(sessionId);
        return session != null ? session.getParticipants() : Collections.emptyList();
    }

    public void startGroupSession(String sessionId) {
        if (!activeGroupSessions.containsKey(sessionId)) {
            activeGroupSessions.put(sessionId, new GroupSession());
        }
    }

    public void addParticipant(String sessionId, String username) {
        GroupSession session = activeGroupSessions.get(sessionId);
        if (session != null) {
            session.addParticipant(username);
        }
    }

    // 群聊会话内部类
    private static class GroupSession {
        private final List<String> participants = new CopyOnWriteArrayList<>();
        private final Map<String, byte[]> audioBuffers = new ConcurrentHashMap<>();

        public void addParticipant(String username) {
            participants.add(username);
        }

        public List<String> getParticipants() {
            return Collections.unmodifiableList(participants);
        }

        // 混音逻辑...
        public byte[] mixAudio() {
            // 实现混音算法
            return new byte[0];
        }
    }
    public void initAudioDevices() throws LineUnavailableException {
        // 初始化麦克风
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
        microphone.open(AUDIO_FORMAT);

        // 初始化扬声器
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
        speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        speaker.open(AUDIO_FORMAT);
    }

    /**
     * 开始语音会话
     * @param sessionId 会话ID（群聊ID或私聊用户ID）
     */
    public void startSession(String sessionId) {
        activeSessions.add(sessionId);
        conferenceSessions.putIfAbsent(sessionId, new CopyOnWriteArrayList<>());
        System.out.println("[LOG] startSession called, sessionId=" + sessionId);
        if (!isRunning.get()) {
            isRunning.set(true);
            startAudioCapture();
        }
    }

    /**
     * 停止语音会话
     */
    public void stopSession(String sessionId) {
        activeSessions.remove(sessionId);
        conferenceSessions.remove(sessionId);

        if (activeSessions.isEmpty()) {
            isRunning.set(false);
        }
    }

    /**
     * 开始音频捕获线程
     */
    private void startAudioCapture() {
        executorService.submit(() -> {
            microphone.start();
            byte[] buffer = new byte[AUDIO_BUFFER_SIZE];

            while (isRunning.get()) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    processAndSendAudio(Arrays.copyOf(buffer, bytesRead));
                }
            }

            microphone.stop();
        });
    }

    /**
     * 处理并发送音频数据
     */
    private void processAndSendAudio(byte[] rawData) {
        try {
            byte[] encodedData = encoder.encodeAudio(rawData);
            List<AudioPacket> packets = packetizer.packetize(encodedData);

            // 发送到所有活跃会话
            activeSessions.forEach(sessionId -> {
                executorService.submit(() -> {
                    // 实际网络发送逻辑应在此实现
                    sendPacketsToNetwork(sessionId, packets);
                });
            });
        } catch (Exception e) {
            System.err.println("音频编码失败: " + e.getMessage());
        }
    }

    /**
     * 接收网络音频数据包
     */
    private String getCanonicalSessionId(String sessionId) {
        if (conferenceSessions.containsKey(sessionId)) {
            return sessionId;
        }
        // 尝试反转 sessionId
        String[] parts = sessionId.split("_to_");
        if (parts.length == 2) {
            String reversed = parts[1] + "_to_" + parts[0];
            if (conferenceSessions.containsKey(reversed)) {
                return reversed;
            }
        }
        return sessionId; // 默认返回原始
    }

    public void receivePackets(String sessionId, List<AudioPacket> packets) {
        String canonicalId = getCanonicalSessionId(sessionId);
        System.out.println("[LOG] receivePackets called, sessionId=" + sessionId + ", canonicalId=" + canonicalId + ", packets.size=" + (packets == null ? 0 : packets.size()));
        if (!conferenceSessions.containsKey(canonicalId)) {
            System.out.println("[LOG] conferenceSessions 不包含 sessionId: " + canonicalId);
            startSession(canonicalId);
            return;
        }
        conferenceSessions.get(canonicalId).addAll(packets);
        processReceivedAudio(canonicalId);
    }

    /**
     * 处理接收到的音频数据
     */
    private void processReceivedAudio(String sessionId) {
        System.out.println("[LOG] processReceivedAudio called, sessionId=" + sessionId);
        executorService.submit(() -> {
            try {
                List<AudioPacket> packets = conferenceSessions.get(sessionId);
                System.out.println("[LOG] packets.size=" + (packets == null ? 0 : packets.size()));
                byte[] audioData = packetizer.depacketize(packets);
                System.out.println("[LOG] depacketize 完成, audioData.length=" + (audioData == null ? 0 : audioData.length));
                byte[] decodedData = encoder.decodeAudio(audioData);
                System.out.println("[LOG] decodeAudio 完成, decodedData.length=" + (decodedData == null ? 0 : decodedData.length));
                playAudio(decodedData);
                // 清除已处理的数据包
                packets.clear();
            } catch (Exception e) {
                System.err.println("[LOG][" + sessionId + "] 数据包处理失败: " + e.getMessage());
            }
        });
    }

    /**
     * 播放音频数据
     */
    private void playAudio(byte[] audioData) {
        System.out.println("[LOG] playAudio called, audioData.length=" + (audioData == null ? 0 : audioData.length));
        speaker.start();
        speaker.write(audioData, 0, audioData.length);
        System.out.println("播放音频数据，长度: " + audioData.length + " 字节");
    }

    /**
     * 启动Socket客户端，连接到目标主机
     */
    public void connectToPeer(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        this.voiceHost = host;
        this.voicePort = port;
        System.out.println("[LOG] connectToPeer: voiceHost=" + voiceHost + ", voicePort=" + voicePort);
        startReceiveThread();
    }

    /**
     * 启动主Socket接收线程（非音频专用）
     */
    private void startReceiveThread() {
        receiveThread = new Thread(() -> {
            try {
                while (!socket.isClosed()) {
                    String sessionId = (String) in.readObject();
                    List<AudioPacket> packets = (List<AudioPacket>) in.readObject();
                    receivePackets(sessionId, packets);
                }
            } catch (Exception e) {
                if (!socket.isClosed())
                    System.err.println("主Socket接收失败: " + e.getMessage());
            }
        }, "MainSocketReceiveThread");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    /**
     * 客户端连接到服务器的语音转发端口（持久连接）
     */
    public void connectToVoiceServer(String serverHost, int serverPort) throws IOException {
        if (voiceSocket != null && !voiceSocket.isClosed()) return;
        voiceSocket = new Socket(serverHost, serverPort);
        voiceOut = new ObjectOutputStream(voiceSocket.getOutputStream());
        voiceIn = new ObjectInputStream(voiceSocket.getInputStream());
        this.voiceHost = serverHost;
        this.voicePort = serverPort;
        System.out.println("[LOG] connectToVoiceServer: voiceHost=" + voiceHost + ", voicePort=" + voicePort);
        // 启动接收线程（接收服务器转发的音频包）
        voiceReceiveThread = new Thread(() -> {
            try {
                while (!voiceSocket.isClosed()) {
                    String sessionId = (String) voiceIn.readObject();
                    List<AudioPacket> packets = (List<AudioPacket>) voiceIn.readObject();
                    receivePackets(sessionId, packets);
                }
            } catch (Exception e) {
                System.err.println("音频Socket接收失败: " + e.getMessage());
            }
        });
        voiceReceiveThread.setDaemon(true);
        voiceReceiveThread.start();
    }

    /**
     * 通过服务器转发音频包（需包含目标sessionId）
     */
    private void sendPacketsToNetwork(String sessionId, List<AudioPacket> packets) {
        System.out.println("[LOG] sendPacketsToNetwork called, sessionId=" + sessionId + ", packets.size=" + (packets == null ? 0 : packets.size()));
        System.out.println("[LOG] voiceHost=" + voiceHost + ", voicePort=" + voicePort);
        // 只通过服务器转发
        if (voiceOut != null && voiceSocket != null && !voiceSocket.isClosed()) {
            try {
                synchronized (voiceOut) {
                    // 发送目标sessionId和音频包，服务器收到后转发到对应用户
                    voiceOut.writeObject(sessionId);
                    voiceOut.writeObject(packets);
                    voiceOut.flush();
                }
                System.out.println("[LOG] 数据已发送: sessionId=" + sessionId + ", packets.size=" + packets.size());
            } catch (IOException e) {
                System.err.println("音频Socket发送失败: " + e.getMessage());
            }
        } else {
            System.out.println("[LOG] voiceSocket 未连接，未发送");
        }
    }

    /**
     * 关闭Socket和相关资源
     */
    public void closeSocket() {
        try {
            if (receiveThread != null) receiveThread.interrupt();
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // 忽略
        }
        try {
            if (voiceReceiveThread != null) voiceReceiveThread.interrupt();
            if (voiceIn != null) voiceIn.close();
            if (voiceOut != null) voiceOut.close();
            if (voiceSocket != null) voiceSocket.close();
        } catch (IOException e) {
            // 忽略
        }
    }

    /**
     * 清理资源
     */
    public void shutdown() {
        isRunning.set(false);
        executorService.shutdown();
        closeSocket(); // 新增：关闭Socket

        if (microphone != null) {
            microphone.stop();
            microphone.close();
            microphone = null;
        }
        if (speaker != null) {
            speaker.close();
        }
    }

    // 会议管理方法
    public boolean isSessionActive(String sessionId) {
        return activeSessions.contains(sessionId);
    }

    public Set<String> getActiveSessions() {
        return Collections.unmodifiableSet(activeSessions);
    }
    // 用于测试用例设置网络处理器接口
    public interface NetworkHandler {
        void handlePackets(String sessionId, List<AudioPacket> packets);
    }

    private NetworkHandler networkHandler; // 新增字段

    public void setNetworkHandler(NetworkHandler handler) {
        this.networkHandler = handler;
    }

    // 为测试添加一个公开方法，允许外部提供原始音频数据
    public void processAndSendAudioForTest(byte[] rawData) {
        try {
            byte[] encodedData = encoder.encodeAudio(rawData);
            List<AudioPacket> packets = packetizer.packetize(encodedData);

            // 使用测试提供的 NetworkHandler，而不是实际发送
            if (networkHandler != null) {
                networkHandler.handlePackets("test_session_123", packets);
            }
        } catch (Exception e) {
            System.err.println("测试音频处理失败: " + e.getMessage());
        }
    }

}
