package com.chat.file;

import java.io.*;
import java.util.*;

public class FileHistoryManager {
    // 文件历史存储路径: Documents/ChatFiles/用户名/files_{type}_{id}.dat
    public static File getHistoryFile(String user, boolean isGroup, String id) {
        String base = System.getProperty("user.home") + File.separator + "Documents" + File.separator + "ChatFiles" + File.separator + user;
        new File(base).mkdirs();
        String type = isGroup ? "group" : "private";
        return new File(base, "files_" + type + "_" + id + ".dat");
    }

    public static List<FileRecord> loadHistory(String user, boolean isGroup, String id) {
        File file = getHistoryFile(user, isGroup, id);
        if (!file.exists() || file.length() == 0) return new ArrayList<>();
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (List<FileRecord>) ois.readObject();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void saveHistory(String user, boolean isGroup, String id, List<FileRecord> records) {
        File file = getHistoryFile(user, isGroup, id);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(records);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addRecord(String user, boolean isGroup, String id, FileRecord record) {
        List<FileRecord> list = loadHistory(user, isGroup, id);
        list.add(record);
        saveHistory(user, isGroup, id, list);
    }
}
