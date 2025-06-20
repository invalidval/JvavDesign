package com.chat.file;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileTransferManager {
    private static final String FILE_STORAGE_PATH = "files/";

    static {
        new File(FILE_STORAGE_PATH).mkdirs();
    }

    public static void uploadFile(Socket socket, File file, String targetId, boolean isGroup) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(file)) {

            // 发送文件元数据
            dos.writeUTF("/FILE");
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());
            dos.writeUTF(targetId);
            dos.writeBoolean(isGroup);

            // 发送文件内容
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
            dos.flush();
        }
    }

    public static void downloadFile(Socket socket, String fileId, String savePath, FileTransferListener listener) {
        new Thread(() -> {
            try {
                // 发送下载请求
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF("/DOWNLOAD " + fileId);

                // 读取服务器响应
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                String response = dis.readUTF();

                if (response.equals("FILE_DATA")) {
                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();

                    try (FileOutputStream fos = new FileOutputStream(savePath)) {
                        byte[] buffer = new byte[4096];
                        long received = 0;

                        while (received < fileSize) {
                            int bytesRead = dis.read(buffer);
                            if (bytesRead == -1) break;
                            fos.write(buffer, 0, bytesRead);
                            received += bytesRead;

                            int progress = (int)(received * 100 / fileSize);
                            if (listener != null) {
                                SwingUtilities.invokeLater(() -> listener.onProgress(progress));
                            }
                        }

                        if (listener != null) {
                            SwingUtilities.invokeLater(() -> listener.onComplete(savePath));
                        }
                    }
                } else {
                    throw new IOException("服务器响应错误");
                }

            } catch (IOException e) {
                if (listener != null) {
                    SwingUtilities.invokeLater(() -> listener.onError(e.getMessage()));
                }
            }
        }).start();
    }
}
