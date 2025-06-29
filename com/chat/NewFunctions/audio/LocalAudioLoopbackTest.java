package com.chat.NewFunctions.audio;

import javax.sound.sampled.*;
import java.util.Scanner;

public class LocalAudioLoopbackTest {
    public static void main(String[] args) throws Exception {
        AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);

        TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
        SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);

        microphone.open(format);
        speaker.open(format);

        microphone.start();
        speaker.start();

        byte[] buffer = new byte[4096];
        System.out.println("本地音频回放测试已启动，按回车键停止...");
        Thread loopThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        speaker.write(buffer, 0, bytesRead);
                    }
                }
            } catch (Exception e) {
                System.err.println("音频回放异常: " + e.getMessage());
            }
        });
        loopThread.start();

        new Scanner(System.in).nextLine(); // 等待用户输入
        loopThread.interrupt();
        microphone.stop();
        microphone.close();
        speaker.stop();
        speaker.close();
        System.out.println("测试结束。");
    }
}

