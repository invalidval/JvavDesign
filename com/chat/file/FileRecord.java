package com.chat.file;

import java.io.Serializable;

public class FileRecord implements Serializable {
    public String fileId;
    public String fileName;
    public long fileSize;
    public String sender;
    public String receiver; // 群聊为群名，私聊为对方用户名
    public boolean isGroup;
    public long timestamp;

    public FileRecord(String fileId, String fileName, long fileSize, String sender, String receiver, boolean isGroup, long timestamp) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.sender = sender;
        this.receiver = receiver;
        this.isGroup = isGroup;
        this.timestamp = timestamp;
    }
}

