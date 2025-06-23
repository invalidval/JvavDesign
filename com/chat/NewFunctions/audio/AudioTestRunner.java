package com.chat.NewFunctions.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AudioTestRunner {
    private static final AudioFormat TEST_FORMAT = new AudioFormat(
            44100, 16, 2, true, false
    );
    private static final int TEST_DATA_SIZE = 4096;

    public static void main(String[] args) throws Exception {
        System.out.println("=== 开始语音聊天功能测试 ===");

        // 测试1：音频编码解码功能测试
        //testAudioEncodeDecode();

        // 测试2：完整语音聊天流程测试
        testVoiceChatSession();

        System.out.println("=== 所有测试完成 ===");
    }

    /**
     * 测试音频编码和解码功能
     */
    private static void testAudioEncodeDecode() throws Exception {
        System.out.println("\n[测试1] 音频编码解码功能测试...");

        AudioEncoder encoder = new AudioEncoder();

        // 生成测试音频数据（模拟PCM格式）
        byte[] rawAudio = new byte[TEST_DATA_SIZE];
        Arrays.fill(rawAudio, (byte) (Math.random() * 127));

        // 编码测试
        System.out.println("> 原始音频数据长度: " + rawAudio.length + " 字节");
        byte[] encodedAudio = encoder.encodeAudio(rawAudio);
        System.out.println("> 编码后音频数据长度: " + encodedAudio.length + " 字节");

        // 解码测试
        byte[] decodedAudio = encoder.decodeAudio(encodedAudio);
        System.out.println("> 解码后音频数据长度: " + decodedAudio.length + " 字节");

        // 验证长度一致性（由于有损编码，内容不会完全一致）
        if (decodedAudio.length == rawAudio.length) {
            System.out.println("✅ 音频编码解码测试通过（长度匹配）");
        } else {
            System.out.println("❌ 音频编码解码测试失败：长度不一致");
        }
    }

    /**
     * 测试完整的语音聊天流程
     */
    private static void testVoiceChatSession() throws Exception {
        System.out.println("\n[测试2] 完整语音聊天流程测试...");

        VoiceChatManager sender = new VoiceChatManager();
        VoiceChatManager receiver = new VoiceChatManager();

        try {
            // 初始化音频设备（这里仅模拟，不创建真实设备）
            System.out.println("> 初始化音频设备...");
            try {
                sender.initAudioDevices();
                receiver.initAudioDevices();
            } catch (LineUnavailableException e) {
                System.out.println("⚠ 音频设备初始化失败（跳过真实设备测试）");
            }

            // 创建测试会话
            String sessionId = "test_session_123";
            System.out.println("> 创建会话: " + sessionId);
            sender.startSession(sessionId);
            receiver.startSession(sessionId);

            // 生成测试数据
            byte[] rawAudio = new byte[TEST_DATA_SIZE];
            Arrays.fill(rawAudio, (byte) (Math.random() * 127));

            // 模拟数据包发送
            final CountDownLatch latch = new CountDownLatch(1);

            // 添加模拟数据处理器
            MockNetworkHandler networkHandler = new MockNetworkHandler(receiver, sessionId, latch);
            sender.setNetworkHandler(networkHandler);

            // 模拟发送数据
            System.out.println("> 模拟音频发送...");
            sender.processAndSendAudioForTest(rawAudio);

            // 等待接收处理完成（超时10秒）
            if (latch.await(10, TimeUnit.SECONDS)) {
                System.out.println("> 音频数据成功接收并处理");
                System.out.println("✅ 语音聊天流程测试通过");
            } else {
                System.out.println("❌ 语音聊天流程测试失败：数据接收超时");
            }

            // 结束会话
            sender.stopSession(sessionId);
            receiver.stopSession(sessionId);
        } finally {
            // 清理资源
            sender.shutdown();
            receiver.shutdown();
        }
    }


    static class MockNetworkHandler implements VoiceChatManager.NetworkHandler {
        private final VoiceChatManager receiver;
        private final String sessionId;
        private final CountDownLatch latch;

        public MockNetworkHandler(VoiceChatManager receiver, String sessionId, CountDownLatch latch) {
            this.receiver = receiver;
            this.sessionId = sessionId;
            this.latch = latch;
        }

        @Override
        public void handlePackets(String sessionId, List<AudioPacket> packets) {
            System.out.println("📦 网络传输: " + packets.size() + " 个数据包 -> " + sessionId);
            receiver.receivePackets(sessionId, packets);
            latch.countDown();
        }
    }
}