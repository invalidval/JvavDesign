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

    public static void uploadFile(Socket socket, File file, String targetId, boolean isGroup, String sender) throws IOException {
        // === 独立Socket实现 ===
        try (Socket fileSocket = new Socket(socket.getInetAddress(), socket.getPort());
             DataOutputStream dos = new DataOutputStream(fileSocket.getOutputStream());
             DataInputStream dis = new DataInputStream(fileSocket.getInputStream());
             FileInputStream fis = new FileInputStream(file)) {
            // 1. 发送文件命令，增加sender
            String command = String.format("/FILE %s %d %s %s %s",
                    file.getName(), file.length(), targetId, isGroup, sender);
            dos.writeUTF(command);
            dos.flush();

            // 2. 发送文件内容
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
            dos.flush();
//             3. 可选：读取服务器返回的上传结果
             String resp = dis.readUTF();
             System.out.println("上传响应: " + resp);
        }
    }

    public static void downloadFile(Socket socket, String fileId, String savePath, FileTransferListener listener) {
        new Thread(() -> {
            try (Socket fileSocket = new Socket(socket.getInetAddress(), socket.getPort());
                 DataOutputStream dos = new DataOutputStream(fileSocket.getOutputStream());
                 DataInputStream dis = new DataInputStream(fileSocket.getInputStream())) {
                // 发送下载命令
                dos.writeUTF("/DOWNLOAD " + fileId);
                dos.flush();

                // 读取响应
                String response = dis.readUTF();
                if ("FILE_DATA".equals(response)) {
                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();
                    File saveFile = new File(savePath);
                    try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                        byte[] buffer = new byte[4096];
                        long received = 0;
                        int count = 0;
                        while (received < fileSize) {
                            int bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize - received));
                            if (bytesRead == -1) break;
                            fos.write(buffer, 0, bytesRead);
                            received += bytesRead;
                            count++;
                            if (listener != null) {
                                int progress = (int)(received * 100 / fileSize);
                                SwingUtilities.invokeLater(() -> listener.onProgress(progress));
                            }
                        }
                        if (listener != null) {
                            SwingUtilities.invokeLater(() -> listener.onComplete(savePath));
                        }
                    }
                } else if (response.startsWith("ERROR:")) {
                    String error = response.substring(6);
                    if (listener != null) {
                        SwingUtilities.invokeLater(() -> listener.onError(error));
                    }
                } else {
                    String error = "服务器响应错误或流数据错位: " + response;
                    if (listener != null) {
                        SwingUtilities.invokeLater(() -> listener.onError(error));
                    }
                }
            } catch (IOException e) {
                String error = "下载文件时出错: " + e.getMessage();
                if (listener != null) {
                    SwingUtilities.invokeLater(() -> listener.onError(error));
                }
            }
        }).start();
    }
}