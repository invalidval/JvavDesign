package com.chat.NewFunctions.audio;

import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
    public void receivePackets(String sessionId, List<AudioPacket> packets) {
        if (!conferenceSessions.containsKey(sessionId)) {
            return;
        }

        conferenceSessions.get(sessionId).addAll(packets);
        processReceivedAudio(sessionId);
    }

    /**
     * 处理接收到的音频数据
     */
    private void processReceivedAudio(String sessionId) {
        executorService.submit(() -> {
            try {
                List<AudioPacket> packets = conferenceSessions.get(sessionId);
                byte[] audioData = packetizer.depacketize(packets);
                byte[] decodedData = encoder.decodeAudio(audioData);
                playAudio(decodedData);

                // 清除已处理的数据包
                packets.clear();
            } catch (Exception e) {
                System.err.println("[" + sessionId + "] 数据包处理失败: " + e.getMessage());
            }
        });
    }

    /**
     * 播放音频数据
     */
    private void playAudio(byte[] audioData) {
        speaker.start();
        speaker.write(audioData, 0, audioData.length);
    }

    /**
     * 网络发送方法（需根据实际网络框架实现）
     */
    private void sendPacketsToNetwork(String sessionId, List<AudioPacket> packets) {
        // 示例：模拟网络发送
        System.out.println("[" + sessionId + "] 发送 " + packets.size() + " 个数据包");
    }

    /**
     * 清理资源
     */
    public void shutdown() {
        isRunning.set(false);
        executorService.shutdown();

        if (microphone != null) {
            microphone.close();
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
