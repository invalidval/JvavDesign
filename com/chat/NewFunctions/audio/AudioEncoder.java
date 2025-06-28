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
        // 设置音频属性，参数与采集端一致
        audioAttributes = new AudioAttributes();
        audioAttributes.setCodec("aac"); // 你也可以用"libmp3lame"或其他支持的编码器
        audioAttributes.setBitRate(64000); // 16k采样率下64kbps较合适
        audioAttributes.setChannels(2); // 单声道
        audioAttributes.setSamplingRate(44100); // 采集端采样率

        // 设置编码属性
        encodingAttributes = new EncodingAttributes();
        encodingAttributes.setInputFormat("wav"); // 原始数据应封装为wav
        encodingAttributes.setAudioAttributes(audioAttributes);
    }

    /**
     * 编码音频数据为AAC/MP3
     * @param rawData 原始PCM音频数据
     * @return 编码后的字节数组
     */
    public byte[] encodeAudio(byte[] rawData) throws Exception {
        try {
            // 先将PCM数据封装为WAV临时文件
            File wavInput = File.createTempFile("raw_input", ".wav");
            writePcmToWav(rawData, wavInput, 16000, 1, 16);
            File encodedOutput = File.createTempFile("encoded_output", ".aac");

            MultimediaObject inputMultimedia = new MultimediaObject(wavInput);
            encoder.encode(inputMultimedia, encodedOutput, encodingAttributes);

            byte[] encodedBytes = readAllBytes(encodedOutput);

            wavInput.delete();
            encodedOutput.delete();

            return encodedBytes;
        } catch (Exception e) {
            throw new Exception("音频编码失败: " + e.getMessage(), e);
        }
    }

    // 新增：将PCM数据写为WAV文件
    private void writePcmToWav(byte[] pcmData, File wavFile, int sampleRate, int channels, int bitsPerSample) throws IOException {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int totalDataLen = dataSize + 36;
        try (FileOutputStream fos = new FileOutputStream(wavFile)) {
            fos.write(new byte[] {'R','I','F','F'});
            fos.write(intToLittleEndian(totalDataLen));
            fos.write(new byte[] {'W','A','V','E','f','m','t',' '});
            fos.write(intToLittleEndian(16)); // Subchunk1Size
            fos.write(shortToLittleEndian((short)1)); // AudioFormat PCM
            fos.write(shortToLittleEndian((short)channels));
            fos.write(intToLittleEndian(sampleRate));
            fos.write(intToLittleEndian(byteRate));
            fos.write(shortToLittleEndian((short)(channels * bitsPerSample / 8)));
            fos.write(shortToLittleEndian((short)bitsPerSample));
            fos.write(new byte[] {'d','a','t','a'});
            fos.write(intToLittleEndian(dataSize));
            fos.write(pcmData);
        }
    }
    private byte[] intToLittleEndian(int value) {
        return new byte[] {
            (byte)(value & 0xff),
            (byte)((value >> 8) & 0xff),
            (byte)((value >> 16) & 0xff),
            (byte)((value >> 24) & 0xff)
        };
    }
    private byte[] shortToLittleEndian(short value) {
        return new byte[] {
            (byte)(value & 0xff),
            (byte)((value >> 8) & 0xff)
        };
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

