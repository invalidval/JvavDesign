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
        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);

        // 1. 发送文件命令
        String command = String.format("/file %s %d %s %s",
                file.getName(), file.length(), targetId, isGroup);
        System.out.println("准备发送文件命令: " + command);
        writer.println(command);
        writer.flush();

        // 2. 等待一小段时间确保命令被处理
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. 发送文件内容
        try (FileInputStream fis = new FileInputStream(file)) {
            OutputStream out = socket.getOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        }

    }

    public static void downloadFile(Socket socket, String fileId, String savePath, FileTransferListener listener) {
        new Thread(() -> {
            try {
                // 使用PrintWriter发送命令
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println("/DOWNLOAD " + fileId);  // 与服务器端的注册命令大小写一致
                System.out.println("发送下载请求: /DOWNLOAD " + fileId);
                writer.flush();

                // 给服务器一点处理时间
                Thread.sleep(7000);

                // 使用DataInputStream读取响应
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                String response = dis.readUTF();
                System.out.println("收到服务器响应: " + response);

                if ("FILE_DATA".equals(response)) {
                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();
                    System.out.println("准备下载文件: " + fileName + ", 大小: " + fileSize + "字节");

                    File saveFile = new File(savePath);
                    try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                        byte[] buffer = new byte[4096];
                        long received = 0;
                        int count = 0;

                        while (received < fileSize) {
                            int bytesRead = dis.read(buffer);
                            if (bytesRead == -1) break;
                            fos.write(buffer, 0, bytesRead);
                            received += bytesRead;
                            count++;

                            if (count % 100 == 0) {
                                System.out.println("下载进度: " + (received * 100 / fileSize) + "%");
                            }

                            if (listener != null) {
                                int progress = (int)(received * 100 / fileSize);
                                SwingUtilities.invokeLater(() -> listener.onProgress(progress));
                            }
                        }

                        System.out.println("文件下载完成: " + savePath);
                        if (listener != null) {
                            SwingUtilities.invokeLater(() -> listener.onComplete(savePath));
                        }
                    }
                } else {
                    String error = "服务器响应错误: " + response;
                    System.err.println(error);
                    if (listener != null) {
                        SwingUtilities.invokeLater(() -> listener.onError(error));
                    }
                }

            } catch (IOException | InterruptedException e) {
                String error = "下载文件时出错: " + e.getMessage();
                System.err.println(error);
                e.printStackTrace();
                if (listener != null) {
                    SwingUtilities.invokeLater(() -> listener.onError(error));
                }
            }
        }).start();
    }
}
