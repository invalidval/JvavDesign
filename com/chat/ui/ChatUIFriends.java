package com.chat.ui;

import com.chat.client.Client;
import com.chat.client.MessageObserver;
import com.chat.file.FileTransferListener;
import com.chat.file.FileTransferManager;
import com.chat.ui.ChatApp;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
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

    public ChatUIFriends(Client client, JFrame parentFrame, String currentUser, ChatWindowLimitProvider limitProvider) {
        this.client = client;
        this.parentFrame = parentFrame;
        this.currentUser = currentUser;
        this.limitProvider = limitProvider; // 接收 ChatWindowLimitProvider 实例
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
        if (!privateChats.containsKey(friendName) && totalOpen >= max) {
            JOptionPane.showMessageDialog(parentFrame, "已达到最大聊天窗口数(" + max + ")，请先关闭其他窗口！", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        PrivateChatWindow win = privateChats.get(friendName);
        if (win == null || !win.isDisplayable()) {
            win = new PrivateChatWindow(friendName);
            win.setSize(600, 400);
            privateChats.put(friendName, win);
        }
        win.setTitle("私聊 - " + friendName);
        win.setLocationRelativeTo(null);
        win.setVisible(true);
        win.toFront();
    }

    @Override
    public void onMessageReceived(String message) {
        if (message == null) return;
        if (message.startsWith("FRIENDS:")) {
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
        } else if (message.startsWith("[私聊]")) {
            int idx1 = message.indexOf("] ");
            int idx2 = message.indexOf(":", idx1 + 2);
            if (idx1 != -1 && idx2 != -1) {
                String sender = message.substring(idx1 + 2, idx2).trim();
                String msg = message.substring(idx2 + 1).trim();
                PrivateChatWindow win = privateChats.computeIfAbsent(sender, fn -> {
                    PrivateChatWindow w = new PrivateChatWindow(fn);
                    w.setVisible(true);
                    return w;
                });
                win.appendMessage(sender + ": " + msg);
                win.setVisible(true);
            }
        }else if (message.startsWith("FILE_NOTIFY:")) {
            String[] parts = message.split(":");
            String fileId = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);
            String sender = parts[4];
            String targetId = parts[5];

            // 打开或创建私聊窗口
            PrivateChatWindow win = privateChats.computeIfAbsent(sender, fn -> {
                PrivateChatWindow w = new PrivateChatWindow(fn);
                w.setVisible(true);
                return w;
            });

            // 在私聊窗口中显示文件接收提示
            win.showFileReceiveDialog(fileId, fileName, fileSize, sender);
        }
    }

    // 私聊窗口内部类
    class PrivateChatWindow extends JFrame {
        private JTextArea chatArea;
        private JTextField inputField;
        private String friendName;

        public PrivateChatWindow(String friendName) {
            super("与 " + friendName + " 私聊");
            this.friendName = friendName;
            setSize(400, 300);
            setLayout(new BorderLayout());

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
                    client.sendMessage("/f"); // 刷新好友列表
                }
            });
            // 新增：历史消息弹窗逻辑
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

            chatArea = new JTextArea();
            chatArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(chatArea);

            inputField = new JTextField();
            JButton sendButton = new JButton("发送");

            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendButton, BorderLayout.EAST);

            sendButton.addActionListener(e -> sendMessage());
            inputField.addActionListener(e -> sendMessage());

            add(topPanel, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);
            add(inputPanel, BorderLayout.SOUTH);

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
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(buttonPanel, BorderLayout.NORTH);
            bottomPanel.add(inputPanel, BorderLayout.CENTER);

            add(bottomPanel, BorderLayout.SOUTH);

            // 关闭窗口时从privateChats移除
            this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            this.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    privateChats.remove(friendName);
                }
            });
        }

        private void sendMessage() {
            String message = inputField.getText().trim();
            if (!message.isEmpty()) {
                client.sendMessage("/p " + friendName + " " + message);
                appendMessage("我: " + message);
                inputField.setText("");
            }
        }

        public void appendMessage(String msg) {
            chatArea.append(msg + "\n");
        }

        public void showFileReceiveDialog(String fileId, String fileName, long fileSize, String sender) {
            appendMessage(sender + " 发送了文件: " + fileName);
            int option = JOptionPane.showConfirmDialog(this,
                    sender + " 发送了文件: " + fileName + "\n是否下载?",
                    "收到文件",
                    JOptionPane.YES_NO_OPTION);

            if (option == JOptionPane.YES_OPTION) {
                System.out.println("对话框选择: YES");  // 验证选项
                System.out.println("开始下载文件: " + fileName);
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setSelectedFile(new File(fileName));
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
                                    JOptionPane.showMessageDialog(PrivateChatWindow.this, "文件下载完成!");
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
    }
}

