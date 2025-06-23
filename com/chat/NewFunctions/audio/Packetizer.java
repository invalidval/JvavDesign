package com.chat.NewFunctions.audio;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 音频数据包处理器
 * 功能：将大块音频数据分割为网络传输包，以及重组接收到的数据包
 */
public class Packetizer {
    private static  int packetSize = 1024; // 默认每个包1KB

    /**
     * 将音频数据分割为传输包
     * @param audioData 原始音频字节数组
     * @return 分包后的AudioPacket列表
     */
    public static List<AudioPacket> packetize(byte[] audioData) {
        List<AudioPacket> packets = new ArrayList<>();
        int sequenceNumber = 0;
        int offset = 0;

        while (offset < audioData.length) {
            int chunkSize = Math.min(packetSize, audioData.length - offset);
            byte[] chunk = new byte[chunkSize];
            System.arraycopy(audioData, offset, chunk, 0, chunkSize);

            packets.add(new AudioPacket(sequenceNumber++, chunk, offset));
            offset += chunkSize;
        }

        return packets;
    }

    /**
     * 将接收到的数据包重组为完整音频数据
     * @param packets 接收到的数据包列表
     * @return 重组后的完整音频字节数组
     * @throws PacketProcessException 当数据包损坏时抛出
     */
    public static byte[] depacketize(List<AudioPacket> packets) throws PacketProcessException {
        // 按序列号排序确保顺序正确
        packets.sort(Comparator.comparingInt(AudioPacket::getSequence));

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (AudioPacket packet : packets) {
                outputStream.write(packet.getData());
            }
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new PacketProcessException("数据包重组失败: " + e.getMessage());
        }
    }

    /**
     * 设置每个数据包的大小（字节数）
     * @param size 新的包大小（必须大于0）
     */
    public void setPacketSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("包大小必须为正数");
        }
        this.packetSize = size;
    }
}

/**
 * 数据包处理异常
 */
class PacketProcessException extends Exception {
    public PacketProcessException(String message) {
        super(message);
    }
}