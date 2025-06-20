package com.chat.file;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileDatabase {
    private static Map<String, FileInfo> files = new ConcurrentHashMap<>();
    private static Map<String, Set<FileInfo>> groupFiles = new ConcurrentHashMap<>();
    private static Map<String, Set<FileInfo>> privateFiles = new ConcurrentHashMap<>();

    public static void addFile(FileInfo fileInfo) {
        files.put(fileInfo.getId(), fileInfo);
        if (fileInfo.isGroup()) {
            groupFiles.computeIfAbsent(fileInfo.getTargetId(), k -> new HashSet<>()).add(fileInfo);
        } else {
            String key = fileInfo.getUploaderId() + "_" + fileInfo.getTargetId();
            privateFiles.computeIfAbsent(key, k -> new HashSet<>()).add(fileInfo);
        }
    }

    public static FileInfo getFileInfo(String fileId) {
        return files.get(fileId);
    }

    public static Set<FileInfo> getGroupFiles(String groupId) {
        return groupFiles.getOrDefault(groupId, Collections.emptySet());
    }

    public static Set<FileInfo> getPrivateFiles(String user1, String user2) {
        Set<FileInfo> result = new HashSet<>();
        result.addAll(privateFiles.getOrDefault(user1 + "_" + user2, Collections.emptySet()));
        result.addAll(privateFiles.getOrDefault(user2 + "_" + user1, Collections.emptySet()));
        return result;
    }
}
