package com.chat.NewFunctions.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioSystem;

/**
 * 服务器本地音频播放工具类
 * 用于测试播放PCM音频数据
 */
public class DebugAudioPlayer {
    private static SourceDataLine debugSpeakerLine = null;
    private static final float SAMPLE_RATE = 44100.0f;
    private static final int CHANNELS = 2;
    private static final int SAMPLE_SIZE = 16;
    private static final boolean SIGNED = true;
    private static final boolean LITTLE_ENDIAN = false;

    private static void initDebugSpeaker() {
        if (debugSpeakerLine != null) return;
        try {
            AudioFormat format = new AudioFormat(
                    SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, SIGNED, LITTLE_ENDIAN
            );
            debugSpeakerLine = AudioSystem.getSourceDataLine(format);
            debugSpeakerLine.open(format, 32000);
            debugSpeakerLine.start();
        } catch (Exception e) {
            System.out.println("[DebugAudioPlayer] 本地播放设备初始化失败: " + e.getMessage());
        }
    }

    /**
     * 播放PCM音频数据
     * @param pcm PCM字节流
     */
    public static void playPcm(byte[] pcm) {
        if (debugSpeakerLine == null) initDebugSpeaker();
        if (debugSpeakerLine != null && pcm != null && pcm.length > 0) {
            int validLen = pcm.length - (pcm.length % 2); // 2字节对齐
            debugSpeakerLine.write(pcm, 0, validLen);
        }
    }

    public static void close() {
        if (debugSpeakerLine != null) {
            debugSpeakerLine.drain();
            debugSpeakerLine.close();
            debugSpeakerLine = null;
        }
    }
}

