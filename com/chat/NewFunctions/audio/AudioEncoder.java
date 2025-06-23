package com.chat.NewFunctions.audio;

import ws.schild.jave.*;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;


import java.io.*;

public class AudioEncoder {
    private AudioAttributes audioAttributes;
    private final EncodingAttributes encodingAttributes;
    private final Encoder encoder;

    public AudioEncoder() {
        encoder = new Encoder();
        // 设置音频属性
        audioAttributes = new AudioAttributes();
        audioAttributes.setCodec("aac");  // 可替换为 "aac" 或其他支持的编码器
        audioAttributes.setBitRate(128000);
        audioAttributes.setChannels(2);
        audioAttributes.setSamplingRate(44100);

        // 设置编码属性
        encodingAttributes = new EncodingAttributes();
        encodingAttributes.setInputFormat("aac");
        encodingAttributes.setAudioAttributes(audioAttributes);
    }

    /**
     * 编码音频数据为MP3
     * @param rawData 原始PCM音频数据
     * @return 编码后的字节数组
     */
    public byte[] encodeAudio(byte[] rawData) throws Exception {
        try {
            File rawInput = File.createTempFile("raw_input", ".pcm");
            File encodedOutput = File.createTempFile("encoded_output", ".mp3");

            try (FileOutputStream fos = new FileOutputStream(rawInput)) {
                fos.write(rawData);
            }

            MultimediaObject inputMultimedia = new MultimediaObject(rawInput);
            encoder.encode(inputMultimedia, encodedOutput, encodingAttributes);

            byte[] encodedBytes = readAllBytes(encodedOutput);

            rawInput.delete();
            encodedOutput.delete();

            return encodedBytes;
        } catch (Exception e) {
            throw new Exception("音频编码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解码MP3音频为原始PCM数据（需要配置解码器支持）
     * @param encodedData 已编码的音频字节数组
     * @return 原始PCM字节流
     */
    public byte[] decodeAudio(byte[] encodedData) throws Exception {
        try {
            File encodedInput = File.createTempFile("encoded_input", ".mp3");
            File pcmOutput = File.createTempFile("decoded_output", ".pcm");

            try (FileOutputStream fos = new FileOutputStream(encodedInput)) {
                fos.write(encodedData);
            }

            MultimediaObject inputMultimedia = new MultimediaObject(encodedInput);
            AudioAttributes pcmAttrs = new AudioAttributes();
            pcmAttrs.setCodec("pcm_s16le"); // 解码为原始PCM
            pcmAttrs.setChannels(2);
            pcmAttrs.setSamplingRate(44100);

            EncodingAttributes decodeAttr = new EncodingAttributes();
            decodeAttr.setOutputFormat( "wav");
            decodeAttr.setAudioAttributes(pcmAttrs);

            encoder.encode(inputMultimedia, pcmOutput, decodeAttr);

            byte[] rawPcm = readAllBytes(pcmOutput);

            encodedInput.delete();
            pcmOutput.delete();

            return rawPcm;
        } catch (Exception e) {
            throw new Exception("音频解码失败: " + e.getMessage(), e);
        }
    }

    private byte[] readAllBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return in.readAllBytes();
        }
    }

    public void setAudioAttributes(AudioAttributes audioAttributes) {
        this.audioAttributes = audioAttributes;
        this.encodingAttributes.setAudioAttributes(audioAttributes);
    }
}
