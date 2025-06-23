package com.chat.NewFunctions.image;

import java.io.*;
import java.net.Socket;

public class ImageManager {
    /**
     * 发送图片到服务器
     */
    public static void sendImage(OutputStream out, File imageFile, String targetUser) throws IOException {
        if (imageFile == null || !imageFile.exists()) {
            throw new FileNotFoundException("图片文件不存在");
        }
        DataOutputStream dos = new DataOutputStream(out);
        // 1. 发送图片命令和元数据
        dos.writeUTF("/IMAGE " + imageFile.getName() + " " + imageFile.length() + " " + targetUser);
        dos.flush();
        // 2. 发送图片二进制数据
        try (FileInputStream fis = new FileInputStream(imageFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, read);
            }
            dos.flush();
        }
    }

    /**
     * 发送图片到服务器（包含发送者信息，兼容图片专用socket协议）
     */
    public static void sendImage(OutputStream out, File imageFile, String targetUser, String sender) throws IOException {
        if (imageFile == null || !imageFile.exists()) {
            throw new FileNotFoundException("图片文件不存在");
        }
        DataOutputStream dos = new DataOutputStream(out);
        // 1. 发送图片命令和元数据，包含发送者
        dos.writeUTF("/IMAGE " + imageFile.getName() + " " + imageFile.length() + " " + targetUser + " " + sender);
        dos.flush();
        // 2. 发送图片二进制数据
        try (FileInputStream fis = new FileInputStream(imageFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, read);
            }
            dos.flush();
        }
    }

    /**
     * 接收图片并保存到本地
     * @param in 输入流
     * @param savePath 保存路径
     * @param fileSize 文件大小
     * @throws IOException IO异常
     */
    public static void receiveImage(InputStream in, String savePath, long fileSize) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(savePath)) {
            byte[] buffer = new byte[8192];
            long remaining = fileSize;
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (read == -1) break;
                fos.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    /**
     * 判断文件是否为图片（简单判断后缀）
     */
    public static boolean isImageFile(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".bmp");
    }

    /**
     * 通过新socket连接图片端口并发送图片及元信息（包含发送者信息）
     * @param serverHost 服务器地址
     * @param imagePort 图片端口
     * @param imageFile 图片文件
     * @param targetUser 目标用户
     * @param sender 发送者
     * @throws IOException IO异常
     */
    public static void sendImageToServer(String serverHost, int imagePort, File imageFile, String targetUser, String sender) throws IOException {
        try (Socket socket = new Socket(serverHost, 18989)) { // 端口同步为18989
            sendImage(socket.getOutputStream(), imageFile, targetUser, sender);
            // 新增：读取服务端返回消息并弹窗提示
            DataInputStream in = new DataInputStream(socket.getInputStream());
            String resp = in.readUTF();
            System.out.println("图片发送结果: " + resp);
            javax.swing.SwingUtilities.invokeLater(() -> {
                javax.swing.JOptionPane.showMessageDialog(null, resp, "图片发送结果", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            });
        }
    }
}
