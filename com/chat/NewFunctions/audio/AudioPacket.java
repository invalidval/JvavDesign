package com.chat.NewFunctions.audio;


/**
 * 音频数据包实体类
 * 位置：与AudioEncoder、Packetizer同包（NewFunctions.audio）
 */
public class AudioPacket {
    private final int sequence;      // 包序列号（用于排序）
    private final byte[] data;       // 音频数据内容
    private final long timestamp;    // 时间戳（毫秒）
    private final int offset;        // 在原始数据中的偏移量

    public AudioPacket(int sequence, byte[] data, int offset) {
        this.sequence = sequence;
        this.data = data.clone();  // 防御性拷贝
        this.timestamp = System.currentTimeMillis();
        this.offset = offset;
    }

    // Getter方法
    public int getSequence() {
        return sequence;
    }

    public byte[] getData() {
        return data.clone();  // 返回拷贝保证数据不可变
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return String.format(
                "AudioPacket[seq=%d, offset=%d, dataLen=%d]",
                sequence, offset, data.length
        );
    }
}