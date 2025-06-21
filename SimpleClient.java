import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * 简单的测试客户端
 */
public class SimpleClient {
    
    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 8888);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            Scanner scanner = new Scanner(System.in);
            
            System.out.println("连接到服务器成功！");
            System.out.println("使用说明：");
            System.out.println("/r 用户名 密码    —— 注册");
            System.out.println("/l 用户名 密码    —— 登录");
            System.out.println("/h               —— 查看历史消息");
            System.out.println("/h 用户名        —— 查看与指定用户的私聊记录");
            System.out.println("/p 用户名 消息   —— 私聊");
            System.out.println("直接输入内容为群聊消息");
            System.out.println("输入 'quit' 退出");
            
            // 启动接收消息的线程
            Thread receiveThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readUTF()) != null) {
                        System.out.println("服务器: " + serverMessage);
                    }
                } catch (EOFException e) {
                    System.out.println("服务器关闭了连接。");
                } catch (IOException e) {
                    System.out.println("与服务器的连接已断开");
                }
            });
            receiveThread.setDaemon(true);
            receiveThread.start();
            
            // 主线程处理用户输入
            String userInput;
            while ((userInput = scanner.nextLine()) != null) {
                if ("quit".equalsIgnoreCase(userInput)) {
                    break;
                }
                out.writeUTF(userInput);
            }
            
            socket.close();
            scanner.close();
            
        } catch (IOException e) {
            System.out.println("连接服务器失败: " + e.getMessage());
        }
    }
}