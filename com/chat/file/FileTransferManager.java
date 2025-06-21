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
        com.chat.client.Client client = com.chat.client.Client.getClientBySocket(socket);
        if (client == null) {
            throw new IOException("无法找到与Socket关联的客户端实例");
        }
        DataOutputStream dos = client.getDataOutputStream();

        // 1. 发送文件命令
        String command = String.format("/FILE %s %d %s %s",
                file.getName(), file.length(), targetId, isGroup);
        System.out.println("准备发送文件命令: " + command);
        dos.writeUTF(command);
        dos.flush();

        // 2. 发送文件内容 (不再需要sleep)
        try (FileInputStream fis = new FileInputStream(file)) {
            OutputStream out = dos;
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
            com.chat.client.Client client = com.chat.client.Client.getClientBySocket(socket);
            boolean paused = false;
            if (client != null) {
                client.pauseListening();
                paused = true;
            } else {
                if (listener != null) {
                    SwingUtilities.invokeLater(() -> listener.onError("无法找到客户端实例"));
                }
                return;
            }

            try {
                // 使用Client持有的统一流
                DataOutputStream dos = client.getDataOutputStream();
                DataInputStream dis = client.getDataInputStream();

                // 发送下载命令
                dos.writeUTF("/DOWNLOAD " + fileId);
                dos.flush();

                // 读取响应
                String response = dis.readUTF();

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
                            int bytesRead = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize - received));
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
                } else if (response.startsWith("ERROR:")) {
                    String error = response.substring(6);
                    System.err.println("服务器错误: " + error);
                    if (listener != null) {
                        SwingUtilities.invokeLater(() -> listener.onError(error));
                    }
                } else {
                    // 这里处理的就是你遇到的读取错位问题，服务器的响应不是预期的 "FILE_DATA" 或 "ERROR:"
                    String error = "服务器响应错误或流数据错位: " + response;
                    System.err.println(error);
                    if (listener != null) {
                        SwingUtilities.invokeLater(() -> listener.onError(error));
                    }
                }

            } catch (IOException e) {
                String error = "下载文件时出错: " + e.getMessage();
                System.err.println(error);
                e.printStackTrace();
                if (listener != null) {
                    SwingUtilities.invokeLater(() -> listener.onError(error));
                }
            } finally {
                if (paused && client != null) {
                    client.resumeListening();
                }
            }
        }).start();
    }
}