package com.chat.ui;

import com.chat.client.Client;
import com.chat.client.MessageObserver;
import com.chat.file.FileTransferListener;
import com.chat.file.FileTransferManager;
import com.chat.file.FileRecord;
import com.chat.file.FileHistoryXmlManager;

import com.chat.NewFunctions.audio.VoiceChatManager;
import javax.sound.sampled.LineUnavailableException;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChatUIFriends implements MessageObserver {
    private JPanel friendsPanel;
    private JPanel friendsListPanel;
    private Client client;
    private JFrame parentFrame;
    private String currentUser;
    private Map<String, PrivateChatWindow> privateChats = new ConcurrentHashMap<>();
    private Map<String, Boolean> friendOnlineStatus = new ConcurrentHashMap<>();
    private java.util.List<String> currentFriends = new ArrayList<>();
    private ChatWindowLimitProvider limitProvider;
    private String serverHost; // 新增字段，保存服务器host
    // 新增：语音管理器
    private final VoiceChatManager voiceChatManager = new VoiceChatManager();

    public ChatUIFriends(Client client, JFrame parentFrame, String currentUser, ChatWindowLimitProvider limitProvider) {
        this.client = client;
        this.parentFrame = parentFrame;
        this.currentUser = currentUser;
        this.limitProvider = limitProvider;
        // 修正：用client.getHost()获取服务器host，保证图片socket连接正确
        this.serverHost = client.getHost();
        createFriendsPanel();
    }

    public JPanel getPanel() {
        return friendsPanel;
    }

    // 新增：进入好友列表时请求所有好友的在线状态
    public void requestFriendsOnlineStatus() {
        if (currentFriends == null || currentFriends.isEmpty()) return;
        StringBuilder sb = new StringBuilder("/online");
        for (String f : currentFriends) {
            if (!f.trim().isEmpty()) sb.append(" ").append(f.trim());
        }
        client.sendMessage(sb.toString());
    }

    private void createFriendsPanel() {
        friendsPanel = new JPanel();
        friendsPanel.setLayout(new BorderLayout());

        friendsListPanel = new JPanel();
        friendsListPanel.setLayout(new BoxLayout(friendsListPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(friendsListPanel);

        JButton addFriendButton = new JButton("添加好友");
        addFriendButton.addActionListener(e -> {
            String friendName = JOptionPane.showInputDialog(parentFrame, "请输入好友用户名：");
            if (friendName != null && !friendName.trim().isEmpty()) {
                client.sendMessage("/a " + friendName.trim());
            }
        });

        friendsPanel.add(scrollPane, BorderLayout.CENTER);
        friendsPanel.add(addFriendButton, BorderLayout.SOUTH);
    }

    private void showFriendsPanel(java.util.List<String> friends) {
        currentFriends = new ArrayList<>(friends);
        friendsListPanel.removeAll();

        // 分组：在线和离线
        java.util.List<String> onlineFriends = new ArrayList<>();
        java.util.List<String> offlineFriends = new ArrayList<>();
        for (String friend : friends) {
            if (friend == null || friend.trim().isEmpty()) continue;
            Boolean online = friendOnlineStatus.getOrDefault(friend.trim(), false);
            if (online) {
                onlineFriends.add(friend.trim());
            } else {
                offlineFriends.add(friend.trim());
            }
        }

        // 在线好友区域
        if (!onlineFriends.isEmpty()) {
            JLabel onlineLabel = new JLabel("在线好友");
            onlineLabel.setFont(onlineLabel.getFont().deriveFont(Font.BOLD));
            onlineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            friendsListPanel.add(onlineLabel);
            friendsListPanel.add(Box.createVerticalStrut(5));
            for (String friend : onlineFriends) {
                JPanel friendCard = new JPanel(new BorderLayout());
                friendCard.setMaximumSize(new Dimension(600, 40));
                friendCard.setPreferredSize(new Dimension(600, 40));
                friendCard.setMinimumSize(new Dimension(600, 40));
                friendCard.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                friendCard.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel friendLabel = new JLabel(friend);
                JLabel statusLabel = new JLabel("[在线]");
                statusLabel.setForeground(new Color(0, 153, 0));
                JButton chatButton = new JButton("私聊");
                chatButton.addActionListener(e -> openPrivateChatWindow(friend));

                JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                leftPanel.setOpaque(false);
                leftPanel.add(friendLabel);
                leftPanel.add(statusLabel);

                friendCard.add(leftPanel, BorderLayout.CENTER);
                friendCard.add(chatButton, BorderLayout.EAST);
                friendsListPanel.add(friendCard);
                friendsListPanel.add(Box.createVerticalStrut(6));
            }
        }

        // 离线好友区域
        if (!offlineFriends.isEmpty()) {
            JLabel offlineLabel = new JLabel("离线好友");
            offlineLabel.setFont(offlineLabel.getFont().deriveFont(Font.BOLD));
            offlineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            friendsListPanel.add(offlineLabel);
            friendsListPanel.add(Box.createVerticalStrut(5));
            for (String friend : offlineFriends) {
                JPanel friendCard = new JPanel(new BorderLayout());
                friendCard.setMaximumSize(new Dimension(600, 40));
                friendCard.setPreferredSize(new Dimension(600, 40));
                friendCard.setMinimumSize(new Dimension(600, 40));
                friendCard.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                friendCard.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel friendLabel = new JLabel(friend);
                JLabel statusLabel = new JLabel("[离线]");
                statusLabel.setForeground(Color.GRAY);
                JButton chatButton = new JButton("私聊");
                chatButton.addActionListener(e -> openPrivateChatWindow(friend));

                JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                leftPanel.setOpaque(false);
                leftPanel.add(friendLabel);
                leftPanel.add(statusLabel);

                friendCard.add(leftPanel, BorderLayout.CENTER);
                friendCard.add(chatButton, BorderLayout.EAST);
                friendsListPanel.add(friendCard);
                friendsListPanel.add(Box.createVerticalStrut(6));
            }
        }

        if (onlineFriends.isEmpty() && offlineFriends.isEmpty()) {
            JLabel noFriendsLabel = new JLabel("暂无好友");
            noFriendsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            friendsListPanel.add(noFriendsLabel);
        }

        friendsListPanel.revalidate();
        friendsListPanel.repaint();
    }

    // 返回当前已打开的私聊窗口数
    public int getOpenChatWindowCount() {
        int openCount = 0;
        for (PrivateChatWindow win : privateChats.values()) {
            if (win != null && win.isDisplayable()) openCount++;
        }
        return openCount;
    }

    public void openPrivateChatWindow(String friendName) {
        // 统一计数所有聊天窗口（私聊+群聊）
        int max = limitProvider.getMaxChatWindows();
        int totalOpen = limitProvider.getCurrentOpenChatWindowCount();
        PrivateChatWindow win = privateChats.get(friendName);
        if (win != null && win.isDisplayable()) {
            win.setVisible(true);
            win.toFront();
            return;
        }
        if (totalOpen >= max) {
            JOptionPane.showMessageDialog(parentFrame, "已达到最大聊天窗口数(" + max + ")，请先关闭其他窗口！", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        win = new PrivateChatWindow(friendName);
        win.setSize(600, 400);
        privateChats.put(friendName, win);
        win.setTitle("私聊 - " + friendName);
        win.setLocationRelativeTo(null);
        win.setVisible(true);
        win.toFront();
        // 新增：加载未读图片消息（支持ChatApp的pollUnreadImages）
        if (parentFrame.getClass().getSimpleName().equals("ChatApp")) {
            try {
                java.lang.reflect.Method pollMethod = parentFrame.getClass().getMethod("pollUnreadImages", String.class, boolean.class);
                java.util.List<?> imgs = (java.util.List<?>) pollMethod.invoke(parentFrame, friendName, false);
                for (Object img : imgs) {
                    // 兼容反射：先尝试getField，失败再尝试getDeclaredField
                    String sender, fileName, imagePath;
                    try {
                        sender = (String) img.getClass().getField("sender").get(img);
                    } catch (Exception e) {
                        sender = (String) img.getClass().getDeclaredField("sender").get(img);
                    }
                    try {
                        fileName = (String) img.getClass().getField("fileName").get(img);
                    } catch (Exception e) {
                        fileName = (String) img.getClass().getDeclaredField("fileName").get(img);
                    }
                    try {
                        imagePath = (String) img.getClass().getField("imagePath").get(img);
                    } catch (Exception e) {
                        imagePath = (String) img.getClass().getDeclaredField("imagePath").get(img);
                    }
                    win.appendImageMessage(sender, fileName, imagePath);
                }
            } catch (Exception ex) {
                // 反射失败忽略
            }
        }
    }

    @Override
    public void onMessageReceived(String message) {
        System.out.println("收到消息: " + message); // 调试日志
        if (message == null) return;

        if (message.startsWith("[私聊]")) {
            int idx1 = message.indexOf("] ");
            int idx2 = message.indexOf(":", idx1 + 2);
            if (idx1 != -1 && idx2 != -1) {
                String sender = message.substring(idx1 + 2, idx2).trim();
                String msg = message.substring(idx2 + 1).trim();
                saveMessageToLocal(sender, msg); // 保存消息到本地
                PrivateChatWindow win = privateChats.get(sender);
                if (win == null || !win.isDisplayable()) {
                    win = new PrivateChatWindow(sender);
                    privateChats.put(sender, win);
                }
                win.appendMessage(sender + ": " + msg);
            }
        } else if (message.startsWith("IMAGE_NOTIFY:")) {
            String[] parts = message.split(":");
            if (parts.length >= 5) {
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                String sender = parts[3];
                String imagePath = parts[4];
                // 本地化存储图片
                String localImagePath = saveImageToLocal(sender, fileName, imagePath);
                saveMessageToLocal(sender, "[图片] " + fileName + " " + localImagePath); // 保存图片消息到本地
                PrivateChatWindow win = privateChats.get(sender);
                if (win == null || !win.isDisplayable()) {
                    win = new PrivateChatWindow(sender);
                    privateChats.put(sender, win);
                }
                win.appendImageMessage(sender, fileName, localImagePath); // 用本地路径显示
            }
        } else if (message.startsWith("FILE_NOTIFY:")) {
            // 文件通知消息处理
            String[] parts = message.split(":");
            if (parts.length >= 5) {
                String fileId = parts[1];
                String fileName = parts[2];
                long fileSize = Long.parseLong(parts[3]);
                String sender = parts[4];
                PrivateChatWindow win = privateChats.get(sender);
                if (win == null || !win.isDisplayable()) {
                    win = new PrivateChatWindow(sender);
                    privateChats.put(sender, win);
                }
                win.showFileReceiveDialog(fileId, fileName, fileSize, sender);
            }
        } else if (message.startsWith("FRIENDS:")) {
            String[] friends = message.substring(8).split(",");
            java.util.List<String> friendList = Arrays.asList(friends);
            SwingUtilities.invokeLater(() -> {
                showFriendsPanel(friendList);
                // 新增：收到好友列表后请求好友在线状态
                requestFriendsOnlineStatus();
            });
            for (String f : friendList) {
                if (!friendOnlineStatus.containsKey(f)) {
                    friendOnlineStatus.put(f, false);
                }
            }
        } else if (message.startsWith("ONLINE:")) {
            // 处理服务端返回的在线状态
            String[] onlineUsers = message.substring(7).split(",");
            Set<String> onlineSet = new HashSet<>();
            for (String u : onlineUsers) {
                if (!u.trim().isEmpty()) onlineSet.add(u.trim());
            }
            for (String friend : currentFriends) {
                friendOnlineStatus.put(friend, onlineSet.contains(friend));
            }
            SwingUtilities.invokeLater(() -> showFriendsPanel(currentFriends));
        } else if (message.startsWith("STATUS:")) {
            // 服务端推送的好友状态变更
            // 格式：STATUS:用户名:online/offline
            String[] parts = message.split(":");
            if (parts.length == 3) {
                String user = parts[1];
                boolean online = "online".equalsIgnoreCase(parts[2]);
                friendOnlineStatus.put(user, online);
                SwingUtilities.invokeLater(() -> showFriendsPanel(currentFriends));
            }
        } else if (message.startsWith("SUCCESS: 好友添加成功")) {
            JOptionPane.showMessageDialog(parentFrame, message);
            client.sendMessage("/f"); // 刷新好友列表
        } else if (message.startsWith("SUCCESS: 好友删除成功")) {
            JOptionPane.showMessageDialog(parentFrame, message);
            client.sendMessage("/f"); // 刷新好友列表
        } else if (message.startsWith("VOICE_ACCEPTED_BY:")) {
            // 对方同意语音通话
            String accepter = message.substring(18);
            PrivateChatWindow win = privateChats.get(accepter);
            if (win != null) {
                win.onVoiceCallAccepted(accepter);
            }
        }
    }


    private void saveMessageToLocal(String sender, String message) {
        String fileName = "chat_private_" + currentUser + "_" + sender + ".txt";
        File dir = new File(System.getProperty("user.home") + File.separator + "ChatLocalHistory");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, fileName);
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(message + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void onVoiceCallAccepted(String accepter) {
        PrivateChatWindow win = privateChats.get(accepter);
        if (win != null) {
            win.onVoiceCallAccepted(accepter);
        }
    }
    // 私聊窗口内部类
    class PrivateChatWindow extends JFrame {
        private JTextPane chatArea;
        private JTextField inputField;
        private String friendName;
        private JTabbedPane tabbedPane;
        private JPanel chatPanel;
        // 文件Tab集成FileListPanel
        private FileListPanel fileListPanel;
        private JButton sendImageButton;
        // 新增：语音通话相关UI组件
        private JButton voiceCallButton;
        private JButton stopVoiceCallButton;
        private boolean isVoiceCalling = false;
        private String voiceSessionId;
        private int defaultVoicePort = 19999; // 可自定义端口

        public PrivateChatWindow(String friendName) {
            super("与 " + friendName + " 私聊");
            this.friendName = friendName;
            setSize(400, 300);
            setLayout(new BorderLayout());

            tabbedPane = new JTabbedPane();
            chatPanel = new JPanel(new BorderLayout());
            fileListPanel = new FileListPanel(client, currentUser, false, friendName, this);

            // 扩展菜单
            JPopupMenu menu = new JPopupMenu();
            JMenuItem deleteFriendItem = new JMenuItem("删除好友");
            menu.add(deleteFriendItem);
            // 新增：历史消息按钮
            JMenuItem historyItem = new JMenuItem("查看历史消息");
            menu.add(historyItem);

            JButton menuButton = new JButton("⋮");
            menuButton.setFocusable(false);
            menuButton.addActionListener(e -> menu.show(menuButton, 0, menuButton.getHeight()));

            deleteFriendItem.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(this, "确定要删除好友 " + friendName + " 吗？", "确认删除", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    client.sendMessage("/d " + friendName);
                    dispose();
                    privateChats.remove(friendName);
                    client.sendMessage("/f"); // ���新好友列表
                }
            });
            // ��增：历史消息弹窗逻辑
            historyItem.addActionListener(e -> {
                ChatUIHistory historyPanel = new ChatUIHistory(client, this, currentUser, "private", friendName);
                JDialog dialog = new JDialog(this, "历史消息 - " + friendName, true);
                dialog.setContentPane(historyPanel.getPanel());
                dialog.setSize(600, 400);
                dialog.setLocationRelativeTo(this);
                dialog.setVisible(true);
            });

            JPanel topPanel = new JPanel(new BorderLayout());
            topPanel.add(new JLabel("与 " + friendName + " 私聊"), BorderLayout.CENTER);
            topPanel.add(menuButton, BorderLayout.EAST);

            chatArea = new JTextPane();
            chatArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(chatArea);

            // 加载本地聊天记录
            loadLocalChatHistory();
            // 加载本地图片
            loadLocalImages();

            inputField = new JTextField();
            JButton sendButton = new JButton("发送");

            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendButton, BorderLayout.EAST);

            sendButton.addActionListener(e -> sendMessage());
            inputField.addActionListener(e -> sendMessage());

            // 图片发送按钮
            sendImageButton = new JButton("发送图片");
            sendImageButton.addActionListener(e -> sendImageAction());
            inputPanel.add(sendImageButton, BorderLayout.WEST);

            // 新增：语音通话按钮
            voiceCallButton = new JButton("语音通话");
            stopVoiceCallButton = new JButton("挂断");
            stopVoiceCallButton.setEnabled(false);

            voiceCallButton.addActionListener(e -> startVoiceCall());
            stopVoiceCallButton.addActionListener(e -> stopVoiceCall());

            // ========== 修正底部按钮显示 ==========
            // 创建底部面板，包含语音按���和输入区
            JPanel bottomPanel = new JPanel(new BorderLayout());
            JPanel voicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            voicePanel.add(voiceCallButton);
            voicePanel.add(stopVoiceCallButton);
            bottomPanel.add(voicePanel, BorderLayout.NORTH);
            bottomPanel.add(inputPanel, BorderLayout.CENTER);

            chatPanel.add(topPanel, BorderLayout.NORTH);
            chatPanel.add(scrollPane, BorderLayout.CENTER);
            chatPanel.add(bottomPanel, BorderLayout.SOUTH); // 只添加一次底部面板

            tabbedPane.addTab("聊天", chatPanel);
            tabbedPane.addTab("文件", fileListPanel);
            add(tabbedPane, BorderLayout.CENTER);

            //新增文件上传功能
            JButton fileButton = new JButton("发送文件");
            fileButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    try {
                        FileTransferManager.uploadFile(client.getSocket(), selectedFile, friendName, false, currentUser);
                        appendMessage("我: 发送文件 " + selectedFile.getName());
                        JOptionPane.showMessageDialog(this, "文件发送成功!");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this,
                                "文件发送失败: " + ex.getMessage(),
                                "错误",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttonPanel.add(fileButton);

            // 修改inputPanel的添加方式
            JPanel bottomPanel2 = new JPanel(new BorderLayout());
            bottomPanel2.add(buttonPanel, BorderLayout.NORTH);
            bottomPanel2.add(inputPanel, BorderLayout.CENTER);

            add(bottomPanel2, BorderLayout.SOUTH);

            // 关闭窗口时自动挂断语音
            this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            this.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    privateChats.remove(friendName);
                    stopVoiceCall();
                }
            });
        }

        // 加载本地聊天记录
        private void loadLocalChatHistory() {
            String fileName = "chat_private_" + currentUser + "_" + friendName + ".txt";
            File file = new File(System.getProperty("user.home") + File.separator + "ChatLocalHistory", fileName);
            if (file.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 格式: [时间] 发送方: 消息内容
                        int idx1 = line.indexOf("] ");
                        int idx2 = line.indexOf(":", idx1 + 2);
                        if (idx1 != -1 && idx2 != -1) {
                            String time = line.substring(0, idx1 + 1); // [时间]
                            String sender = line.substring(idx1 + 2, idx2).trim();
                            String msg = line.substring(idx2 + 1).trim();
                            if (msg.startsWith("[图片] ")) {
                                String[] parts = msg.split(" ", 3);
                                if (parts.length == 3) {
                                    String imgFileName = parts[1];
                                    String imgPath = parts[2];
                                    appendImageMessage(sender, imgFileName, imgPath);
                                    continue;
                                }
                            }
                            chatArea.getDocument().insertString(chatArea.getDocument().getLength(), time + " " + sender + ": " + msg + "\n", null);
                        } else {
                            chatArea.getDocument().insertString(chatArea.getDocument().getLength(), line + "\n", null);
                        }
                    }
                } catch (Exception e) {
                    // 忽略读取异常
                }
            }
        }

        // 新增：加载本地图片（打开窗口时自动加载）
        private void loadLocalImages() {
            String userHome = System.getProperty("user.home");
            String localDirPath = userHome + File.separator + "ChatLocalHistory" + File.separator + "images" + File.separator + "PrivateChat" + File.separator + currentUser + "_to_" + friendName;
            File localDir = new File(localDirPath);
            if (localDir.exists() && localDir.isDirectory()) {
                File[] images = localDir.listFiles((dir, name) -> name.matches(".*\\.(jpg|jpeg|png|gif|bmp)$"));
                if (images != null) {
                    for (File img : images) {
                        appendImageMessage(friendName, img.getName(), img.getAbsolutePath());
                    }
                }
            }
        }

        private void sendMessage() {
            String message = inputField.getText().trim();
            if (!message.isEmpty()) {
                client.sendMessage("/p " + friendName + " " + message);
                appendMessage("我: " + message);
                inputField.setText("");
            }
        }
//==========================================================================================================================
        // 发���图片动作
        private void sendImageAction() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("选择要发送的图片");
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                java.io.File file = fileChooser.getSelectedFile();
                // 可根据需要校验图片类型/大小
                if (com.chat.NewFunctions.image.ImageManager.isImageFile(file)) {
                    try {
                        int imagePort = 8889; // 与服务端保持一致
                        // 构造本地图片保存路径
                        String userHome = System.getProperty("user.home");
                        String imagesDirPath = userHome + File.separator + "ChatLocalHistory" + File.separator + "images" + File.separator + "PrivateChat" + File.separator + currentUser + "_to_" + friendName;
                        File imagesDir = new File(imagesDirPath);
                        if (!imagesDir.exists()) imagesDir.mkdirs();
                        File destFile = new File(imagesDir, file.getName());
                        int count = 1;
                        String baseName = file.getName();
                        String name = baseName;
                        String ext = "";
                        int dot = baseName.lastIndexOf('.');
                        if (dot > 0) {
                            name = baseName.substring(0, dot);
                            ext = baseName.substring(dot);
                        }
                        while (destFile.exists()) {
                            destFile = new File(imagesDir, name + "_" + count + ext);
                            count++;
                        }
                        java.nio.file.Files.copy(file.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        com.chat.NewFunctions.image.ImageManager.sendImageToServer(serverHost, imagePort, destFile, friendName, currentUser);
                        appendMessage("[图片已发送: " + destFile.getName() + "]");
                        appendImageMessage("我", destFile.getName(), destFile.getAbsolutePath()); // 调用图片显示
                    } catch (Exception ex) {
                        appendMessage("[图片发送失败: " + file.getName() + "]");
                        JOptionPane.showMessageDialog(this, "图片发送失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "请选择图片文件（jpg/png/gif/bmp）", "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        }

        public void appendMessage(String msg) {
            try {
                String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                javax.swing.text.Document doc = chatArea.getDocument();
                doc.insertString(doc.getLength(), "[" + time + "] " + msg + "\n", null);
                // 同步写入本地聊天记录
                saveMessageToLocal("[" + time + "] " + msg);
            } catch (javax.swing.text.BadLocationException e) {
                e.printStackTrace();
            }
        }

        // 保存消息到本地文件
        private void saveMessageToLocal(String msg) {
            String fileName = "chat_private_" + currentUser + "_" + friendName + ".txt";
            File dir = new File(System.getProperty("user.home") + File.separator + "ChatLocalHistory");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, fileName);
            try (java.io.FileWriter fw = new java.io.FileWriter(file, true)) {
                fw.write(msg + "\n");
            } catch (Exception e) {
                // 忽略写入异常
            }
        }

        // ====== 新增：保存消息到本地文件（全局方法，供 onMessageReceived 调用） ======

        public void showFileReceiveDialog(String fileId, String fileName, long fileSize, String sender) {
            appendMessage(sender + " 发送了文件: " + fileName);
            int option = JOptionPane.showConfirmDialog(this,
                    sender + " 发送了文件: " + fileName + "\n是否下载?",
                    "收到文件",
                    JOptionPane.YES_NO_OPTION);

            if (option == JOptionPane.YES_OPTION) {
                // 获取当前系统用户名
                String sysUser = System.getProperty("user.name");
                // 构造C盘文档路径
                String docPath = System.getProperty("user.home") + File.separator + "Documents" + File.separator + "ChatFiles" + File.separator + currentUser;
                File userDir = new File(docPath);
                if (!userDir.exists()) userDir.mkdirs();
                JFileChooser fileChooser = new JFileChooser(userDir);
                fileChooser.setSelectedFile(new File(userDir, fileName));
                if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    String savePath = fileChooser.getSelectedFile().getPath();
                    FileTransferManager.downloadFile(client.getSocket(), fileId, savePath,
                            new FileTransferListener() {
                                @Override
                                public void onProgress(int percentage) {
                                }

                                @Override
                                public void onComplete(String filePath) {
                                    appendMessage(sender + " 发送的文件已下载完成: " + fileName);
                                    JOptionPane.showMessageDialog(PrivateChatWindow.this, "文件下载完成!\n保存路径: " + filePath);
                                }

                                @Override
                                public void onError(String error) {
                                    JOptionPane.showMessageDialog(PrivateChatWindow.this,
                                            "下载失败: " + error,
                                            "错误",
                                            JOptionPane.ERROR_MESSAGE);
                                }
                            });
                }
            }
        }
        // 图片接收与查看
        public void showImageReceiveDialog(String fileName, long fileSize, String sender, String imagePath) {
            // 直接插入图片缩略图，不弹窗，仿微信风格
            appendImageMessage(sender, fileName, imagePath);
        }

        // 聊天窗口插入图片消息（自动加载并显示图片缩略图，可点击查看大图）
        public void appendImageMessage(String sender, String fileName, String imagePath) {
            String tempDir = System.getProperty("java.io.tmpdir");
            String localPath = tempDir + File.separator + fileName;
            new Thread(() -> {
                try {
                    File imageFile = new File(imagePath);
                    if (imageFile.exists()) {
                        // 如果是本地图片��件直接显示
                        displayImage(sender, fileName, imagePath);
                    } else {
                        // 从服务器下载图片
                        try (Socket sock = new Socket(serverHost, 18989);
                             DataOutputStream out = new DataOutputStream(sock.getOutputStream());
                             DataInputStream in = new DataInputStream(sock.getInputStream())) {
                            String cmd = "/IMAGE_DOWNLOAD " + fileName + " " + sender + " " + currentUser;
                            out.writeUTF(cmd);
                            out.flush();
                            String resp = in.readUTF();
                            if (!"IMAGE_DATA".equals(resp)) {
                                SwingUtilities.invokeLater(() -> appendMessage("[图片下载失败: " + resp + "]"));
                                return;
                            }
                            String recvFileName = in.readUTF();
                            long recvFileSize = in.readLong();
                            try (FileOutputStream fos = new FileOutputStream(localPath)) {
                                byte[] buffer = new byte[8192];
                                long remain = recvFileSize;
                                while (remain > 0) {
                                    int read = in.read(buffer, 0, (int)Math.min(buffer.length, remain));
                                    if (read == -1) break;
                                    fos.write(buffer, 0, read);
                                    remain -= read;
                                }
                            }
                            displayImage(sender, fileName, localPath);
                        }
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> appendMessage("[图片加载失败: " + ex.getMessage() + "]"));
                }
            }).start();
        }

        private void displayImage(String sender, String fileName, String imagePath) {
            SwingUtilities.invokeLater(() -> {
                try {
                    javax.swing.text.StyledDocument doc = chatArea.getStyledDocument();
                    doc.insertString(doc.getLength(), sender + " 发送了图片: ", null);
                    ImageIcon icon = new ImageIcon(imagePath);
                    if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
                        doc.insertString(doc.getLength(), "[图片加���失败]\n", null);
                        return;
                    }
                    // 动态缩略图最大边长，取chatArea宽度的1/3，���小100，最大300
                    int chatWidth = chatArea.getWidth() > 0 ? chatArea.getWidth() : 300;
                    int maxThumb = Math.max(100, Math.min(300, chatWidth / 3));
                    int width = icon.getIconWidth();
                    int height = icon.getIconHeight();
                    int newW = width, newH = height;
                    if (width > height && width > maxThumb) {
                        newW = maxThumb;
                        newH = (int) ((double) height / width * maxThumb);
                    } else if (height >= width && height > maxThumb) {
                        newH = maxThumb;
                        newW = (int) ((double) width / height * maxThumb);
                    }
                    Image img = icon.getImage().getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                    ImageIcon thumbIcon = new ImageIcon(img);
                    javax.swing.text.Style style = chatArea.addStyle("imageStyle" + System.nanoTime(), null);
                    javax.swing.text.StyleConstants.setIcon(style, thumbIcon);
                    int insertPos = doc.getLength();
                    doc.insertString(insertPos, "ignored", style);
                    doc.insertString(doc.getLength(), "\n", null);
                    chatArea.setCaretPosition(doc.getLength());
                    // 支持点击预览大图（自适应弹窗）
                    chatArea.addMouseListener(new java.awt.event.MouseAdapter() {
                        public void mouseClicked(java.awt.event.MouseEvent evt) {
                            int pos = chatArea.viewToModel2D(evt.getPoint());
                            if (pos == insertPos) {
                                JDialog dialog = new JDialog(PrivateChatWindow.this, "图片预览", true);
                                JLabel bigLabel = new JLabel();
                                JScrollPane pane = new JScrollPane(bigLabel);
                                dialog.setContentPane(pane);
                                dialog.setSize(600, 600);
                                dialog.setLocationRelativeTo(PrivateChatWindow.this);
                                Runnable updateImage = () -> {
                                    int w = pane.getViewport().getWidth();
                                    int h = pane.getViewport().getHeight();
                                    if (w <= 0 || h <= 0) return;
                                    ImageIcon bigIcon = new ImageIcon(imagePath);
                                    int imgW = bigIcon.getIconWidth();
                                    int imgH = bigIcon.getIconHeight();
                                    int showW = imgW, showH = imgH;
                                    if (imgW > w || imgH > h) {
                                        double scale = Math.min((double) w / imgW, (double) h / imgH);
                                        showW = (int) (imgW * scale);
                                        showH = (int) (imgH * scale);
                                    }
                                    Image scaled = bigIcon.getImage().getScaledInstance(showW, showH, Image.SCALE_SMOOTH);
                                    bigLabel.setIcon(new ImageIcon(scaled));
                                };
                                pane.addComponentListener(new java.awt.event.ComponentAdapter() {
                                    public void componentResized(java.awt.event.ComponentEvent e) {
                                        updateImage.run();
                                    }
                                });
                                dialog.addComponentListener(new java.awt.event.ComponentAdapter() {
                                    public void componentResized(java.awt.event.ComponentEvent e) {
                                        updateImage.run();
                                    }
                                });
                                updateImage.run();
                                dialog.setVisible(true);
                            }
                        }
                    });
                } catch (Exception e) {
                    try {
                        javax.swing.text.Document doc = chatArea.getDocument();
                        doc.insertString(doc.getLength(), "[图片显示失败]\n", null);
                    } catch (javax.swing.text.BadLocationException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }

        // 新增：启动语音通话
        private void startVoiceCall() {
            if (isVoiceCalling) return;
            try {
                // 初始化音频设备
                voiceChatManager.initAudioDevices();
                // 生成唯一会话ID
                voiceSessionId = currentUser + "_to_" + friendName;
//                String voiceSessionId_reverse = friendName + "_to_" + currentUser;
                // 连接服务器语音转发端口（假设端口为19999，可根据实际配置）
                int voicePort = 19999;
                voiceChatManager.connectToVoiceServer(serverHost, voicePort);
                // 启动语音会话
                voiceChatManager.startSession(voiceSessionId);
//                voiceChatManager.startSession(voiceSessionId_reverse);
                appendMessage("[语音通话已开始]"+ voiceSessionId);
                isVoiceCalling = true;
                voiceCallButton.setEnabled(false);
                stopVoiceCallButton.setEnabled(true);
            } catch (LineUnavailableException ex) {
                appendMessage("[语音设备初始化失败] " + ex.getMessage());
            } catch (Exception ex) {
                appendMessage("[语音通话启动失败] " + ex.getMessage());
            }
        }

        // 新增：挂断语音通话
        private void stopVoiceCall() {
            if (!isVoiceCalling) return;
            try {
                voiceChatManager.stopSession(voiceSessionId);
                voiceChatManager.closeSocket();
                appendMessage("[语音通话已挂断]");
            } catch (Exception ex) {
                appendMessage("[挂断失败] " + ex.getMessage());
            }
            isVoiceCalling = false;
            voiceCallButton.setEnabled(true);
            stopVoiceCallButton.setEnabled(false);
        }

        // 对方同意语音通话后建立语音会话
        public void onVoiceCallAccepted(String accepter) {
            if (isVoiceCalling) return;
            try {
                voiceChatManager.initAudioDevices();
                // 这里假设 voiceSessionId 格式为 currentUser + "_to_" + accepter
                voiceSessionId = currentUser + "_to_" + accepter;
                int voicePort = 19999;
                voiceChatManager.connectToVoiceServer(serverHost, voicePort);
                voiceChatManager.startSession(voiceSessionId);
                appendMessage("[语音通话已开始]" );
                isVoiceCalling = true;
                voiceCallButton.setEnabled(false);
                stopVoiceCallButton.setEnabled(true);
            } catch (LineUnavailableException ex) {
                appendMessage("[语音设备初始化失败] " + ex.getMessage());
            } catch (Exception ex) {
                appendMessage("[语音通话启动失败] " + ex.getMessage());
            }
        }
    }
    // 工具方法：将图片复制到本地目录
    private String saveImageToLocal(String sender, String fileName, String imagePath) {
        String userHome = System.getProperty("user.home");
        String localDirPath = userHome + File.separator + "ChatLocalHistory" + File.separator + "images" + File.separator + "PrivateChat" + File.separator + sender + "_to_" + currentUser;
        File localDir = new File(localDirPath);
        if (!localDir.exists()) localDir.mkdirs();
        File src = new File(imagePath);
        File dest = new File(localDir, fileName);
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return dest.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return imagePath; // 失败时返回原路径
        }
    }
}
