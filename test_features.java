import java.io.*;
import java.net.*;

/**
 * 测试漫游功能和历史消息功能
 */
public class test_features {
    
    public static void main(String[] args) {
        System.out.println("=== 功能测试开始 ===");
        
        // 启动服务器
        Thread serverThread = new Thread(() -> {
            try {
                System.out.println("正在启动服务器...");
                com.chat.server.Server.main(new String[]{});
            } catch (IOException e) {
                System.out.println("服务器启动失败: " + e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        
        // 等待服务器启动
        sleep(3000);
        
        try {
            // 测试1：漫游功能测试
            testRoamingFeature();
            
            sleep(2000);
            
            // 测试2：历史消息功能测试
            testHistoryFeature();
            
        } catch (Exception e) {
            System.out.println("测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== 功能测试完成 ===");
    }
    
    /**
     * 测试漫游功能
     */
    private static void testRoamingFeature() throws Exception {
        System.out.println("\n--- 测试1：用户漫游功能 ---");
        
        // 创建第一个客户端连接
        Socket client1 = new Socket("localhost", 8888);
        PrintWriter out1 = new PrintWriter(client1.getOutputStream(), true);
        BufferedReader in1 = new BufferedReader(new InputStreamReader(client1.getInputStream()));
        
        // 注册用户
        out1.println("/r testuser testpass");
        String response = in1.readLine();
        System.out.println("注册响应: " + response);
        
        // 第一个设备登录
        out1.println("/l testuser testpass");
        response = in1.readLine();
        System.out.println("第一个设备登录: " + response);
        
        // 跳过登录后的消息
        skipMessages(in1, 5);
        
        // 发送一条消息确认连接正常
        out1.println("我是第一个设备");
        sleep(500);
        
        // 创建第二个客户端连接（模拟同一用户在另一台设备登录）
        Socket client2 = new Socket("localhost", 8888);
        PrintWriter out2 = new PrintWriter(client2.getOutputStream(), true);
        BufferedReader in2 = new BufferedReader(new InputStreamReader(client2.getInputStream()));
        
        // 第二个设备登录（应该踢掉第一个设备）
        System.out.println("\n第二个设备尝试登录...");
        out2.println("/l testuser testpass");
        response = in2.readLine();
        System.out.println("第二个设备登录: " + response);
        
        // 检查第一个设备是否被踢下线
        sleep(1000);
        try {
            String kickMessage = in1.readLine();
            if (kickMessage != null && kickMessage.contains("另一台设备")) {
                System.out.println("✅ 漫游功能正常: " + kickMessage);
            } else {
                System.out.println("❌ 漫游功能异常: 第一个设备未被踢下线");
            }
        } catch (Exception e) {
            System.out.println("✅ 漫游功能正常: 第一个设备连接已断开");
        }
        
        // 验证第二个设备可以正常使用
        out2.println("我是第二个设备，成功登录");
        sleep(500);
        
        // 清理连接
        try { client1.close(); } catch (Exception e) {}
        try { client2.close(); } catch (Exception e) {}
        
        System.out.println("漫游功能测试完成");
    }
    
    /**
     * 测试历史消息功能
     */
    private static void testHistoryFeature() throws Exception {
        System.out.println("\n--- 测试2：历史消息功能 ---");
        
        // 创建用户A
        Socket clientA = new Socket("localhost", 8888);
        PrintWriter outA = new PrintWriter(clientA.getOutputStream(), true);
        BufferedReader inA = new BufferedReader(new InputStreamReader(clientA.getInputStream()));
        
        // 注册并登录用户A
        outA.println("/r userA passA");
        inA.readLine(); // 注册响应
        outA.println("/l userA passA");
        String loginResponse = inA.readLine();
        System.out.println("用户A登录: " + loginResponse);
        
        // 检查是否显示历史消息
        sleep(500);
        checkHistoryOnLogin(inA);
        
        // 创建用户B
        Socket clientB = new Socket("localhost", 8888);
        PrintWriter outB = new PrintWriter(clientB.getOutputStream(), true);
        BufferedReader inB = new BufferedReader(new InputStreamReader(clientB.getInputStream()));
        
        outB.println("/r userB passB");
        inB.readLine(); // 注册响应
        outB.println("/l userB passB");
        inB.readLine(); // 登录响应
        skipMessages(inB, 5);
        
        // 发送一些测试消息
        System.out.println("\n发送测试消息...");
        outA.println("这是群聊消息1");
        outA.println("这是群聊消息2");
        outB.println("用户B的群聊消息");
        
        // 发送私聊消息
        outA.println("/p userB 你好B，这是私聊消息");
        outB.println("/p userA 你好A，我收到了");
        
        sleep(1000);
        
        // 测试历史消息查询
        System.out.println("\n测试历史消息查询...");
        
        // 查看所有历史消息
        outA.println("/h");
        System.out.println("用户A查看历史消息:");
        readHistoryResponse(inA);
        
        // 查看私聊记录
        outA.println("/h userB");
        System.out.println("\n用户A查看与B的私聊记录:");
        readHistoryResponse(inA);
        
        // 测试重新登录时的历史消息显示
        System.out.println("\n测试重新登录时的历史消息显示...");
        clientA.close();
        sleep(1000);
        
        // 重新登录
        Socket clientA2 = new Socket("localhost", 8888);
        PrintWriter outA2 = new PrintWriter(clientA2.getOutputStream(), true);
        BufferedReader inA2 = new BufferedReader(new InputStreamReader(clientA2.getInputStream()));
        
        outA2.println("/l userA passA");
        String reloginResponse = inA2.readLine();
        System.out.println("用户A重新登录: " + reloginResponse);
        
        // 检查重新登录时的历史消息
        checkHistoryOnLogin(inA2);
        
        // 清理连接
        try { clientA2.close(); } catch (Exception e) {}
        try { clientB.close(); } catch (Exception e) {}
        
        System.out.println("历史消息功能测试完成");
    }
    
    /**
     * 检查登录时的历史消息显示
     */
    private static void checkHistoryOnLogin(BufferedReader in) throws IOException {
        System.out.println("检查登录时的历史消息显示:");
        boolean foundHistory = false;
        for (int i = 0; i < 10; i++) {
            String line = in.readLine();
            if (line == null) break;
            System.out.println("  " + line);
            if (line.contains("最近消息记录")) {
                foundHistory = true;
            }
            if (line.contains("==================")) {
                break;
            }
        }
        if (foundHistory) {
            System.out.println("✅ 登录时历史消息显示正常");
        } else {
            System.out.println("❌ 登录时未显示历史消息");
        }
    }
    
    /**
     * 读取历史消息查询响应
     */
    private static void readHistoryResponse(BufferedReader in) throws IOException {
        boolean foundStart = false;
        for (int i = 0; i < 15; i++) {
            String line = in.readLine();
            if (line == null) break;
            System.out.println("  " + line);
            if (line.contains("===")) {
                if (foundStart) {
                    System.out.println("✅ 历史消息查询功能正常");
                    break;
                } else {
                    foundStart = true;
                }
            }
        }
    }
    
    /**
     * 跳过指定数量的消息
     */
    private static void skipMessages(BufferedReader in, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            String line = in.readLine();
            if (line == null) break;
        }
    }
    
    /**
     * 线程休眠
     */
    private static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
