package com.chat.NewFunctions.audio;

import ws.schild.jave.*;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;


import java.io.*;

import static java.nio.file.Files.readAllBytes;

public class AudioEncoder {
    private AudioAttributes audioAttributes;
    private final EncodingAttributes encodingAttributes;
    private final Encoder encoder;
// ***WARNING***:需要系统环境变量FFMPEG_PATH指向ffmpeg的bin路径
// ***WARNING***:需要系统环境变量FFMPEG_PATH指向ffmpeg的bin路径
// ***WARNING***:需要系统环境变量FFMPEG_PATH指向ffmpeg的bin路径
// 重要的事情说三遍
    public AudioEncoder() {
//        String ffmpegLocation ;
//        String envPath = System.getenv("FFMPEG_PATH");
//        if (envPath != null && !envPath.isEmpty()) {
//            ffmpegLocation = envPath;
//            System.out.println("FFMPEG LOCATION: " + ffmpegLocation);
//        } else {
//            // 默认常用路径（可根据实际情况修改）
//            System.out.println("未配置FFMPEG_PATH环境变量，使用默认路径");
//            ffmpegLocation = "D:/ffmpeg-7.0.2-essentials_build/bin/ffmpeg.exe";
//        }
//        // 校验ffmpeg可执行文件是否存在
//        File ffmpegFile = new File(ffmpegLocation);
//        if (!ffmpegFile.exists()) {
//            throw new RuntimeException("未找到ffmpeg可执行文件: " + ffmpegLocation + "，请检查路径或配置环境变量FFMPEG_PATH");
//        }
//        System.setProperty("ffmpeg.location", ffmpegLocation);
        encoder = new Encoder();
        // 设置音频属性，参数与采集端一致
        audioAttributes = new AudioAttributes();
        audioAttributes.setCodec("aac");
        audioAttributes.setBitRate(64000);
        audioAttributes.setChannels(2);
        audioAttributes.setSamplingRate(44100);
        encodingAttributes = new EncodingAttributes();
        encodingAttributes.setInputFormat("wav");
        encodingAttributes.setAudioAttributes(audioAttributes);
    }

    /**
     * 直接返回原始PCM数据（无损链路）
     * @param rawData 原始PCM音频数据
     * @return PCM数据
     */
    public byte[] encodeAudio(byte[] rawData) {
        // 补齐4字节对齐
        int frameSize = 4;
        if (rawData.length % frameSize != 0) {
            byte[] padded = new byte[rawData.length + (frameSize - (rawData.length % frameSize))];
            System.arraycopy(rawData, 0, padded, 0, rawData.length);
            for (int i = rawData.length; i < padded.length; i++) padded[i] = 0;
            rawData = padded;
        }
        return rawData;
    }

    /**
     * 直接返回PCM数据（无损链路）
     * @param encodedData PCM数据
     * @return PCM数据
     */
    public byte[] decodeAudio(byte[] encodedData) {
        // 补齐4字节对齐
        int frameSize = 4;
        if (encodedData.length % frameSize != 0) {
            byte[] padded = new byte[encodedData.length + (frameSize - (encodedData.length % frameSize))];
            System.arraycopy(encodedData, 0, padded, 0, encodedData.length);
            for (int i = encodedData.length; i < padded.length; i++) padded[i] = 0;
            encodedData = padded;
        }
        return encodedData;
    }

    /**
     * 使用cmd命令行方式调用ffmpeg进行音频转码（如wav转aac）
     * @param inputPath 输入音频文件路径（如wav）
     * @param outputPath 输出音频文件路径（如aac）
     * @throws IOException, InterruptedException
     */
    public void encodeAudioWithCmd(String inputPath, String outputPath) throws IOException, InterruptedException {
        String ffmpegPath = System.getenv("FFMPEG_PATH");
        if (ffmpegPath == null || ffmpegPath.isEmpty()) {
            ffmpegPath = "D:/ffmpeg-7.0.2-essentials_build/bin/ffmpeg.exe";
        } else {
            File ffmpegFile = new File(ffmpegPath);
            if (ffmpegFile.isDirectory()) {
                // 如果是目录，自动补全ffmpeg.exe
                ffmpegPath = new File(ffmpegFile, "ffmpeg.exe").getAbsolutePath();
            }
        }
        File ffmpegFile = new File(ffmpegPath);
        if (!ffmpegFile.exists()) {
            throw new RuntimeException("未找到ffmpeg可执行文件: " + ffmpegPath);
        }
        String[] command = {
                ffmpegPath,
                "-y", // 覆盖输出文件
                "-i", inputPath,
                "-acodec", "aac",
                "-b:a", "64k",
                "-ac", "2",
                "-ar", "44100",
                outputPath
        };
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("ffmpeg转码失败，退出码: " + exitCode);
        }
    }

    /**
     * 使用cmd命令行方式调用ffmpeg进行音频解码（如mp3转wav）
     * @param inputPath 输入音频文件路径（如mp3）
     * @param outputPath 输出音频文件路径（如wav）
     * @throws IOException, InterruptedException
     */
    public void decodeAudioWithCmd(String inputPath, String outputPath) throws IOException, InterruptedException {
        String ffmpegPath = System.getenv("FFMPEG_PATH");
        if (ffmpegPath == null || ffmpegPath.isEmpty()) {
            ffmpegPath = "D:/ffmpeg-7.0.2-essentials_build/bin/ffmpeg.exe";
        } else {
            File ffmpegFile = new File(ffmpegPath);
            if (ffmpegFile.isDirectory()) {
                ffmpegPath = new File(ffmpegFile, "ffmpeg.exe").getAbsolutePath();
            }
        }
        File ffmpegFile = new File(ffmpegPath);
        if (!ffmpegFile.exists()) {
            throw new RuntimeException("未找到ffmpeg可执行文件: " + ffmpegPath);
        }
        String[] command = {
                ffmpegPath,
                "-y",
                "-i", inputPath,
                "-acodec", "pcm_s16le",
                "-ac", "2",
                "-ar", "44100",
                outputPath
        };
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("ffmpeg解码失败，退出码: " + exitCode);
        }
    }
}
