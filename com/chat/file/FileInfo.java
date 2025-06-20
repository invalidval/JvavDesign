package com.chat.file;

public class FileInfo {
    private String id;
    private String name;
    private long size;
    private String uploaderId;
    private String targetId;
    private boolean isGroup;
    private long uploadTime;
    private String path;

    // 构造函数
    public FileInfo(String id, String name, long size, String uploaderId,
                    String targetId, boolean isGroup, String path) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.uploaderId = uploaderId;
        this.targetId = targetId;
        this.isGroup = isGroup;
        this.path = path;
        this.uploadTime = System.currentTimeMillis();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public long getSize() { return size; }
    public String getUploaderId() { return uploaderId; }
    public String getTargetId() { return targetId; }
    public boolean isGroup() { return isGroup; }
    public long getUploadTime() { return uploadTime; }
    public String getPath() { return path; }
}
