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
