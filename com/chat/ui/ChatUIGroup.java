package com.chat.ui;

import com.chat.client.Client;
import com.chat.client.MessageObserver;
import com.chat.file.FileTransferListener;
import com.chat.file.FileTransferManager;
import com.chat.file.FileRecord;
import com.chat.file.FileHistoryXmlManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import com.chat.NewFunctions.audio.VoiceChatManager;
import javax.sound.sampled.LineUnavailableException;

public class ChatUIGroup implements MessageObserver {
    private JPanel groupPanel;
    private JPanel groupListPanel;
    private Client client;
    private JFrame parentFrame;
    private String currentUser;
    private Map<String, GroupChatWindow> groupChats = new ConcurrentHashMap<>();
    private List<String> myGroups = new ArrayList<>();
    private List<String> allGroups = new ArrayList<>(); // 新增：所有群聊列表
    private Map<String, List<String>> groupMembers = new HashMap<>();
    private ChatWindowLimitProvider limitProvider;
    private SubGroupUI subGroupUI;
    // 新增：群聊语音管理器
    private final VoiceChatManager groupVoiceChatManager = new VoiceChatManager();

    public ChatUIGroup(Client client, JFrame parentFrame, String currentUser, ChatWindowLimitProvider limitProvider) {
        this.client = client;
        this.parentFrame = parentFrame;
        this.currentUser = currentUser;
        this.limitProvider = limitProvider;
        this.subGroupUI = new SubGroupUI(client, parentFrame, currentUser);
        createGroupPanel();
        requestGroupList();
    }

    public JPanel getPanel() {
        return groupPanel;
    }

    private void createGroupPanel() {
        groupPanel = new JPanel(new BorderLayout());

        groupListPanel = new JPanel();
        groupListPanel.setLayout(new BoxLayout(groupListPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(groupListPanel);

        JButton createGroupButton = new JButton("创建群聊");
        createGroupButton.addActionListener(e -> showCreateGroupDialog());

        JButton joinGroupButton = new JButton("加入群聊"); // 新增按钮
        joinGroupButton.addActionListener(e -> showJoinGroupDialog()); // 绑定加入群聊逻辑

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(createGroupButton);
        buttonPanel.add(joinGroupButton); // 添加加入群聊按钮

        groupPanel.add(scrollPane, BorderLayout.CENTER);
        groupPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    private void showCreateGroupDialog() {
        // 请求好友列表
        client.sendMessage("/f");
        // 弹窗选择好友
        String friendsRaw = JOptionPane.showInputDialog(parentFrame, "请输入要拉入群聊的好友用户名（用英文逗号分隔）:");
        if (friendsRaw == null || friendsRaw.trim().isEmpty()) return;
        String[] friends = Arrays.stream(friendsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        String groupName = JOptionPane.showInputDialog(parentFrame, "请输入群聊名称:");
        if (groupName == null || groupName.trim().isEmpty()) return;
        StringBuilder sb = new StringBuilder("/cg ");
        sb.append(groupName);
        for (String f : friends) {
            sb.append(" ").append(f);
        }
        client.sendMessage(sb.toString());
    }

    private void showJoinGroupDialog() {
        String groupName = JOptionPane.showInputDialog(parentFrame, "请输入要加入的群聊名称:");
        if (groupName == null || groupName.trim().isEmpty()) return;
        client.sendMessage("/jg " + groupName); // 发送加入群聊请求
    }

    private void showGroupList() {
        System.out.println("[DEBUG] showGroupList() called");
        groupListPanel.removeAll();
        // 标题美化
        JLabel myGroupTitle = new JLabel("我的群聊");
        myGroupTitle.setFont(new Font("微软雅黑", Font.BOLD, 18));
        myGroupTitle.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 0));
        groupListPanel.add(myGroupTitle);
        // 我的群聊分区
        if (myGroups.isEmpty()) {
            System.out.println("[DEBUG] myGroups is empty");
            JLabel emptyLabel = new JLabel("暂无我的群聊");
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            groupListPanel.add(emptyLabel);
        } else {
            System.out.println("[DEBUG] myGroups: " + myGroups);
            for (String group : myGroups) {
                System.out.println("[DEBUG] show myGroup: " + group);
                JPanel groupCard = new JPanel(new BorderLayout()) {
                    Color hoverColor = new Color(220, 240, 255);
                    Color normalColor = new Color(245, 250, 255);
                    boolean hovered = false;
                    {
                        setBackground(normalColor);
                        addMouseListener(new java.awt.event.MouseAdapter() {
                            @Override
                            public void mouseEntered(java.awt.event.MouseEvent e) {
                                setBackground(hoverColor);
                                hovered = true;
                                repaint();
                            }
                            @Override
                            public void mouseExited(java.awt.event.MouseEvent e) {
                                setBackground(normalColor);
                                hovered = false;
                                repaint();
                            }
                            @Override
                            public void mouseClicked(java.awt.event.MouseEvent e) {
                                openGroupChatWindow(group);
                            }
                        });
                    }
                };
                groupCard.setMaximumSize(new Dimension(600, 48));
                groupCard.setPreferredSize(new Dimension(600, 48));
                groupCard.setMinimumSize(new Dimension(600, 48));
                groupCard.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(180,180,180), 1, true),
                        BorderFactory.createEmptyBorder(6, 16, 6, 16)));
                groupCard.setAlignmentX(Component.LEFT_ALIGNMENT);
                JLabel groupLabel = new JLabel(group);
                groupLabel.setFont(new Font("微软雅黑", Font.PLAIN, 16));
                JButton chatButton = new JButton("进入群聊");
                chatButton.setFocusable(false);
                chatButton.addActionListener(e -> openGroupChatWindow(group));
                JButton subGroupButton = new JButton("小组管理");
                subGroupButton.setFocusable(false);
                subGroupButton.addActionListener(e -> showSubGroupDialog(group));
                JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                btnPanel.setOpaque(false);
                btnPanel.add(chatButton);
                btnPanel.add(subGroupButton);
                groupCard.add(groupLabel, BorderLayout.CENTER);
                groupCard.add(btnPanel, BorderLayout.EAST);
                groupListPanel.add(groupCard);
                groupListPanel.add(Box.createVerticalStrut(8));
            }
        }
        // 其他群聊分区（仅展示未加入的群聊）
        if (allGroups != null && !allGroups.isEmpty()) {
            System.out.println("[DEBUG] allGroups: " + allGroups);
            List<String> otherGroups = new ArrayList<>();
            for (String g : allGroups) {
                if (!myGroups.contains(g)) otherGroups.add(g);
            }
            System.out.println("[DEBUG] otherGroups: " + otherGroups);
            if (!otherGroups.isEmpty()) {
                JLabel otherGroupTitle = new JLabel("其他群聊");
                otherGroupTitle.setFont(new Font("微软雅黑", Font.BOLD, 18));
                otherGroupTitle.setBorder(BorderFactory.createEmptyBorder(16, 10, 5, 0));
                groupListPanel.add(otherGroupTitle);
                for (String group : otherGroups) {
                    System.out.println("[DEBUG] show otherGroup: " + group);
                    JPanel groupCard = new JPanel(new BorderLayout());
                    groupCard.setMaximumSize(new Dimension(600, 44));
                    groupCard.setPreferredSize(new Dimension(600, 44));
                    groupCard.setMinimumSize(new Dimension(600, 44));
                    groupCard.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(220,220,220), 1, true),
                            BorderFactory.createEmptyBorder(4, 16, 4, 16)));
                    groupCard.setBackground(new Color(255, 252, 240));
                    groupCard.setAlignmentX(Component.LEFT_ALIGNMENT);
                    JLabel groupLabel = new JLabel(group);
                    groupLabel.setFont(new Font("微软雅黑", Font.PLAIN, 15));
                    JButton joinButton = new JButton("加入群聊");
                    joinButton.setFocusable(false);
                    joinButton.addActionListener(e -> {
                        int ok = JOptionPane.showConfirmDialog(parentFrame, "确定要加入群聊："+group+"？", "加入确认", JOptionPane.YES_NO_OPTION);
                        if (ok == JOptionPane.YES_OPTION) {
                            client.sendMessage("/jg " + group);
                        }
                    });
                    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                    btnPanel.setOpaque(false);
                    btnPanel.add(joinButton);
                    groupCard.add(groupLabel, BorderLayout.CENTER);
                    groupCard.add(btnPanel, BorderLayout.EAST);
                    groupListPanel.add(groupCard);
                    groupListPanel.add(Box.createVerticalStrut(6));
                }
            }
        } else {
            System.out.println("[DEBUG] allGroups is null or empty");
        }
        groupListPanel.revalidate();
        groupListPanel.repaint();
        System.out.println("[DEBUG] showGroupList() finished");
    }

    // ================== 小组信息缓存与唯一性校验相关 ==================
    private Map<String, String> subGroupInfoCache = new HashMap<>();
    // 缓存每个群的小组成员分布，key=groupName，value=Map<subGroupId, Set<member>>
    private Map<String, Map<String, Set<String>>> subGroupMemberMap = new HashMap<>();
    // 小组聊天窗口管理
    private Map<String, SubGroupChatWindow> subGroupChats = new ConcurrentHashMap<>();
    // 新增：记录最近���求小组列表的群名
    private String lastSGListGroupName = null;
    // 新增：缓存每个群的小组面板对象，便于SG_LIST消息刷新
    private Map<String, JPanel> groupSubGroupPanelMap = new HashMap<>();
    public void openSubGroupChatWindow(String groupName, String subGroupId) {
        subGroupUI.openSubGroupChatWindow(groupName, subGroupId);
    }
    // 新增：小组列表消息回调
    public void onSubGroupListReceived(String groupName, String info) {
        if (subGroupUI != null) {
            subGroupUI.onSubGroupListReceived(groupName, info);
        }
    }

    // ================== 小组聊天窗口内部类 ==================
    class SubGroupChatWindow extends JFrame {
        private JTextPane chatArea;
        private JTextField inputField;
        private String groupName, subGroupId;
        private JTabbedPane tabbedPane;
        private JPanel chatPanel;
        private FileListPanel fileListPanel;
        public SubGroupChatWindow(String groupName, String subGroupId) {
            super("小组聊天 - " + groupName + " | 小组ID:" + subGroupId);
            this.groupName = groupName;
            this.subGroupId = subGroupId;
            setSize(600, 400);
            setLayout(new BorderLayout());
            tabbedPane = new JTabbedPane();
            chatPanel = new JPanel(new BorderLayout());
            fileListPanel = new FileListPanel(client, currentUser, true, groupName + "#" + subGroupId, this);
            chatArea = new JTextPane();
            chatArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(chatArea);
            // TODO: 加载本地小组聊天记录（可选）
            inputField = new JTextField();
            JButton sendButton = new JButton("发送");
            sendButton.addActionListener(e -> sendMessage());
            inputField.addActionListener(e -> sendMessage());
            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendButton, BorderLayout.EAST);
            // 图片发送按钮
            JButton sendImageButton = new JButton("发送图片");
            sendImageButton.addActionListener(e -> sendImageAction());
            inputPanel.add(sendImageButton, BorderLayout.WEST);
            chatPanel.add(scrollPane, BorderLayout.CENTER);
            chatPanel.add(inputPanel, BorderLayout.SOUTH);
            tabbedPane.addTab("聊天", chatPanel);
            tabbedPane.addTab("文件", fileListPanel);
            add(tabbedPane, BorderLayout.CENTER);
            // 文件发送按钮
            JButton fileButton = new JButton("发送文件");
            fileButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    try {
                        FileTransferManager.uploadFile(client.getSocket(), selectedFile, groupName + "#" + subGroupId, true, currentUser);
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
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(buttonPanel, BorderLayout.NORTH);
            bottomPanel.add(inputPanel, BorderLayout.CENTER);
            add(bottomPanel, BorderLayout.SOUTH);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    subGroupChats.remove(groupName + "#" + subGroupId);
                }
            });
        }
        private void sendMessage() {
            String msg = inputField.getText().trim();
            if (!msg.isEmpty()) {
                client.sendMessage("/sg_msg " + groupName + " " + subGroupId + " " + msg);
                appendMessage("我: " + msg);
                saveGroupMessageToLocal(groupName, msg); // 修正参数，去掉 currentUser
                inputField.setText("");
            }
        }
        private void sendImageAction() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("选择要发送的图片");
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                if (com.chat.NewFunctions.image.ImageManager.isImageFile(file)) {
                    try {
                        int imagePort = 18989;
                        // 修正：targetUser 只为 groupName
                        com.chat.NewFunctions.image.ImageManager.sendImageToServer(
                                client.getHost(), imagePort, file, groupName, currentUser + ":group");
                        appendMessage("[图片已发送: " + file.getName() + "]");
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
                // 新增：保存群聊消息到本地
                saveGroupMessageToLocal(groupName, msg);
            } catch (javax.swing.text.BadLocationException e) {
                e.printStackTrace();
            }
        }
        // 新增：保存群聊消息到本地
        private void saveGroupMessageToLocal(String groupName, String msg) {
            String dirPath = System.getProperty("user.home") + File.separator + "ChatLocalHistory";
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();
            String fileName = "chat_group_" + groupName + ".txt";
            File file = new File(dir, fileName);
            String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String line = String.format("[%s] %s", time, msg);
            try (java.io.FileWriter fw = new java.io.FileWriter(file, true)) {
                fw.write(line + "\n");
            } catch (Exception e) {
                // 忽略写入异常
            }
        }
        // 可选：图片接收与显示、文件接收等功能可后续补充
    }

    private void requestGroupList() {
        client.sendMessage("/glist");
    }

    // 保存群聊消息到本地文件，带时间戳和发送方ID
    private void saveGroupMessageToLocal(String groupName, String sender, String message) {
        String dirPath = System.getProperty("user.home") + File.separator + "ChatLocalHistory";
        File dir = new File(dirPath);
        if (!dir.exists()) dir.mkdirs();
        String fileName = "chat_group_" + groupName + ".txt";
        File file = new File(dir, fileName);
        String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        String line = String.format("[%s] %s: %s", time, sender, message);
        try (java.io.FileWriter fw = new java.io.FileWriter(file, true)) {
            fw.write(line + "\n");
        } catch (Exception e) {
            // 忽略写入异常
        }
    }

    // 群聊窗口内部类
    class GroupChatWindow extends JFrame {
        private JTextPane chatArea;
        private JTextField inputField;
        private String groupName;
        private JTabbedPane tabbedPane;
        private JPanel chatPanel;
        private FileListPanel fileListPanel; // 替换原有的文件表格
        // 新增：图片消息offset映射（offset -> imagePath）
        private final Map<Integer, String> imageOffsetMap = new HashMap<>();
        private boolean imageMouseListenerAdded = false;
        // 新增：语音通话相关UI组件
        private JButton voiceCallButton;
        private JButton stopVoiceCallButton;
        private boolean isVoiceCalling = false;
        private String voiceSessionId;
        private int defaultVoicePort = 20001; // 群聊默认端口

        public GroupChatWindow(String groupName) {
            super("群聊 - " + groupName);
            this.groupName = groupName;
            setSize(400, 300);
            setLayout(new BorderLayout());

            tabbedPane = new JTabbedPane();
            chatPanel = new JPanel(new BorderLayout());
            // 文件Tab集成FileListPanel
            fileListPanel = new FileListPanel(client, currentUser, true, groupName, this);

            chatArea = new JTextPane();
            chatArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(chatArea);

            // 加载本地群聊聊天记录
            loadLocalGroupChatHistory();

            inputField = new JTextField();
            JButton sendButton = new JButton("发送");

            JPanel inputPanel = new JPanel(new BorderLayout());
            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendButton, BorderLayout.EAST);

            sendButton.addActionListener(e -> sendMessage());
            inputField.addActionListener(e -> sendMessage());

            // 新增群聊图片发送按钮
            JButton sendImageButton = new JButton("发送图片");
            sendImageButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("选择要发送的图片");
                int result = fileChooser.showOpenDialog(this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    if (com.chat.NewFunctions.image.ImageManager.isImageFile(file)) {
                        try {
                            int imagePort = 18989;
                            com.chat.NewFunctions.image.ImageManager.sendImageToServer(
                                    client.getHost(), imagePort, file, groupName, currentUser + ":group");
                            appendImageMessage(currentUser, file.getName(), file.getAbsolutePath());
                            saveGroupMessageToLocal(currentUser, "[图片] " + file.getName() + " " + file.getAbsolutePath());
                        } catch (Exception ex) {
                            appendMessage("[图片发送失败: " + file.getName() + "]");
                            JOptionPane.showMessageDialog(this, "图片发送失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, "请选择图片文件（jpg/png/gif/bmp）", "提示", JOptionPane.WARNING_MESSAGE);
                    }
                }
            });
            inputPanel.add(sendImageButton, BorderLayout.WEST);

            chatPanel.add(scrollPane, BorderLayout.CENTER);
            chatPanel.add(inputPanel, BorderLayout.SOUTH);

            tabbedPane.addTab("聊天", chatPanel);
            tabbedPane.addTab("文件", fileListPanel);
            add(tabbedPane, BorderLayout.CENTER);

            // 可扩展：显示群成员列表
            //新增文件上传功能
            JButton fileButton = new JButton("发送文件");
            fileButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    try {
                        FileTransferManager.uploadFile(client.getSocket(), selectedFile, groupName, true, currentUser);
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

            // 新增：历史消息按钮
            JButton historyButton = new JButton("查看历史消息");
            historyButton.addActionListener(e -> {
                ChatUIHistory historyPanel = new ChatUIHistory(client, this, currentUser, "group", groupName);
                JDialog dialog = new JDialog(this, "群聊历史消息 - " + groupName, true);
                dialog.setContentPane(historyPanel.getPanel());
                dialog.setSize(600, 400);
                dialog.setLocationRelativeTo(this);
                dialog.setVisible(true);
            });

            // 新增群聊语音通话按钮
            voiceCallButton = new JButton("语音通话");
            stopVoiceCallButton = new JButton("挂断");
            stopVoiceCallButton.setEnabled(false);

            voiceCallButton.addActionListener(e -> startVoiceCall());
            stopVoiceCallButton.addActionListener(e -> stopVoiceCall());

            // 新增：底部面板，包含语音按钮和原有按钮
            JPanel voicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            voicePanel.add(voiceCallButton);
            voicePanel.add(stopVoiceCallButton);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttonPanel.add(fileButton);
            buttonPanel.add(historyButton);

            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(voicePanel, BorderLayout.NORTH);
            bottomPanel.add(buttonPanel, BorderLayout.CENTER);
            bottomPanel.add(inputPanel, BorderLayout.SOUTH);

            // 修改底部面板添加方式
            add(bottomPanel, BorderLayout.SOUTH);

            // 窗口关闭时清理资源
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    groupChats.remove(groupName);
                    stopVoiceCall();
                    super.windowClosing(e);
                }
            });
        }

        // 新增：启动语音通话
        private void startVoiceCall() {
            if (isVoiceCalling) return;
            try {
                groupVoiceChatManager.initAudioDevices();
                voiceSessionId = "group_" + groupName;
                String[] options = {"作为主叫（对方需先点被叫）", "作为被叫（先点，等待主叫连接）"};
                int choice = JOptionPane.showOptionDialog(this, "请选择通话角色：", "语音通话",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                if (choice == 0) {
                    String host = JOptionPane.showInputDialog(this, "请输入对方IP（本机可填127.0.0.1）", "127.0.0.1");
                    groupVoiceChatManager.connectToPeer(host, defaultVoicePort);
                } else if (choice == 1) {
                    JOptionPane.showMessageDialog(this, "请等待对方发起连接...", "提示", JOptionPane.INFORMATION_MESSAGE);
                    new Thread(() -> {
                        try {
                            groupVoiceChatManager.startServer(defaultVoicePort);
                        } catch (Exception ex) {
                            SwingUtilities.invokeLater(() -> appendMessage("[语音服务端启动失败] " + ex.getMessage()));
                        }
                    }).start();
                } else {
                    return;
                }
                groupVoiceChatManager.startSession(voiceSessionId);
                appendMessage("[语音通话已开始]");
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
                groupVoiceChatManager.stopSession(voiceSessionId);
                groupVoiceChatManager.closeSocket();
                appendMessage("[语音通话已挂断]");
            } catch (Exception ex) {
                appendMessage("[挂断失败] " + ex.getMessage());
            }
            isVoiceCalling = false;
            voiceCallButton.setEnabled(true);
            stopVoiceCallButton.setEnabled(false);
        }

        private void sendMessage() {
            String message = inputField.getText().trim();
            if (!message.isEmpty()) {
                client.sendMessage("/gs " + groupName + " " + message);
                // 立即本地显示消息，保证体验一致
                appendMessage("我: " + message);
                saveGroupMessageToLocal(currentUser, message);
                inputField.setText("");
            }
        }

        public void appendMessage(String msg) {
            try {
                String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                javax.swing.text.Document doc = chatArea.getDocument();
                doc.insertString(doc.getLength(), "[" + time + "] " + msg + "\n", null);
            } catch (javax.swing.text.BadLocationException e) {
                e.printStackTrace();
            }
        }

        // 新增：群聊图片消息插入与点击大图预览
        public void appendImageMessage(String sender, String fileName, String imagePath) {
            SwingUtilities.invokeLater(() -> {
                try {
                    String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                    javax.swing.text.StyledDocument doc = chatArea.getStyledDocument();
                    doc.insertString(doc.getLength(), "[" + time + "] " + sender + " 发送了图片: ", null);
                    ImageIcon icon = new ImageIcon(imagePath);
                    if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
                        doc.insertString(doc.getLength(), "[图片加载失败]\n", null);
                        return;
                    }
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
                    imageOffsetMap.put(insertPos, imagePath);
                    doc.insertString(doc.getLength(), "\n", null);
                    chatArea.setCaretPosition(doc.getLength());
                    if (!imageMouseListenerAdded) {
                        chatArea.addMouseListener(new java.awt.event.MouseAdapter() {
                            public void mouseClicked(java.awt.event.MouseEvent evt) {
                                int pos = chatArea.viewToModel2D(evt.getPoint());
                                for (Map.Entry<Integer, String> entry : imageOffsetMap.entrySet()) {
                                    int offset = entry.getKey();
                                    if (pos == offset) {
                                        String imgPath = entry.getValue();
                                        JDialog dialog = new JDialog(GroupChatWindow.this, "图片预览", true);
                                        JLabel bigLabel = new JLabel();
                                        JScrollPane pane = new JScrollPane(bigLabel);
                                        dialog.setContentPane(pane);
                                        dialog.setSize(600, 600);
                                        dialog.setLocationRelativeTo(GroupChatWindow.this);
                                        Runnable updateImage = () -> {
                                            int w = pane.getViewport().getWidth();
                                            int h = pane.getViewport().getHeight();
                                            if (w <= 0 || h <= 0) return;
                                            ImageIcon bigIcon = new ImageIcon(imgPath);
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
                                        break;
                                    }
                                }
                            }
                        });
                        imageMouseListenerAdded = true;
                    }
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

        // 加载本地群聊聊天记录，遇到图片消息自动加载缩略图
        private void loadLocalGroupChatHistory() {
            String fileName = "chat_group_" + groupName + ".txt";
            File file = new File(System.getProperty("user.home") + File.separator + "ChatLocalHistory", fileName);
            if (file.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int idx1 = line.indexOf("] ");
                        int idx2 = line.indexOf(":", idx1 + 2);
                        if (idx1 != -1 && idx2 != -1) {
                            String time = line.substring(0, idx1 + 1);
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

        // 保存群聊消息到本地文件，带时间戳和发送方ID，图片消息用[图片] 文件名 路径格式
        private void saveGroupMessageToLocal(String sender, String message) {
            String dirPath = System.getProperty("user.home") + File.separator + "ChatLocalHistory";
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();
            String fileName = "chat_group_" + groupName + ".txt";
            File file = new File(dir, fileName);
            String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String line = String.format("[%s] %s: %s", time, sender, message);
            try (java.io.FileWriter fw = new java.io.FileWriter(file, true)) {
                fw.write(line + "\n");
            } catch (Exception e) {
                // 忽略写入异常
            }
        }

        // 新增：群文件接收弹窗和下载功能
        public void showFileReceiveDialog(String fileId, String fileName, long fileSize, String sender) {
            appendMessage(sender + " 发送了文件: " + fileName);
            int option = JOptionPane.showConfirmDialog(this,
                    sender + " 发送了文件: " + fileName + "\n是否下载?",
                    "收到群文件",
                    JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                String docPath = System.getProperty("user.home") + File.separator + "Documents" + File.separator + "ChatFiles" + File.separator + currentUser + File.separator + groupName;
                File groupDir = new File(docPath);
                if (!groupDir.exists()) groupDir.mkdirs();
                JFileChooser fileChooser = new JFileChooser(groupDir);
                fileChooser.setSelectedFile(new File(groupDir, fileName));
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
                                    JOptionPane.showMessageDialog(GroupChatWindow.this, "文件下载完成!\n保存路径: " + filePath);
                                }
                                @Override
                                public void onError(String error) {
                                    JOptionPane.showMessageDialog(GroupChatWindow.this,
                                            "下载失败: " + error,
                                            "错误",
                                            JOptionPane.ERROR_MESSAGE);
                                }
                            });
                }
            }
        }
    }

    // 新增：获取小组UI面板（如需嵌入主界面）
    public JPanel getSubGroupPanel() {
        return subGroupUI.getPanel();
    }

    // ================== 小组管理对话框 ==================
    private void showSubGroupDialog(String groupName) {
        this.lastSGListGroupName = groupName; // 修复：同步记录当前请求小组列表的群名
        subGroupUI.showSubGroupDialog(groupName);
    }

    // updateSubGroupListPanel 相关界面刷新全部交由 subGroupUI
    private void updateSubGroupListPanel(String groupName, JPanel subGroupListPanel, JDialog parentDialog) {
        // 只做转发
        subGroupUI.getClass(); // 保证subGroupUI已初始化
        // 这里直接调用subGroupUI的刷新方法
        // 由于原方法是private，需在SubGroupUI中将updateSubGroupListPanel改为public
        subGroupUI.updateSubGroupListPanel(groupName, subGroupListPanel, parentDialog);
    }

    // 实现MessageObserver��口方法
    @Override
    public void onMessageReceived(String message) {
        System.out.println("[DEBUG][ChatUIGroup] 收到消息: " + message); // 调试信息
        if (message == null) return;
        // 新增：处理群聊历史消息
        if (message.startsWith("=== 群聊 ")) {
            System.out.println("[DEBUG][ChatUIGroup] 处理群聊历史消息: " + message);
            // 解析群名
            int idx1 = message.indexOf("群聊 ") + 3;
            int idx2 = message.indexOf(" 的消息记录", idx1);
            String groupName = (idx1 != -1 && idx2 != -1) ? message.substring(idx1, idx2).trim() : null;
            if (groupName != null) {
                GroupChatWindow win = groupChats.get(groupName);
                if (win == null || !win.isDisplayable()) {
                    win = new GroupChatWindow(groupName);
                    groupChats.put(groupName, win);
                }
                win.setVisible(true);
                win.toFront();
                // 清空原有内容，准备加载历史
                win.chatArea.setText("");
            }
            return;
        }
        // 追加群聊历史消息内容到窗口
        if (message.startsWith("[")) {
            System.out.println("[DEBUG][ChatUIGroup] 追加群聊历史消息内容: " + message);
            // 判断是否为历史消息格式
            if (message.contains("[群聊]")) {
                // 跳过，已由后续逻辑处理
            } else {
                // 追加到最近打开的群聊窗口
                if (!groupChats.isEmpty()) {
                    GroupChatWindow lastWin = null;
                    for (GroupChatWindow w : groupChats.values()) {
                        if (w.isVisible()) lastWin = w;
                    }
                    if (lastWin != null) {
                        try {
                            lastWin.chatArea.getDocument().insertString(lastWin.chatArea.getDocument().getLength(), message + "\n", null);
                            // 新增：解析历史消息并写入本地
                            int idx1 = message.indexOf("] ");
                            int idx2 = message.indexOf(":", idx1 + 2);
                            if (idx1 != -1 && idx2 != -1) {
                                String time = message.substring(0, idx1 + 1); // [时间]
                                String sender = message.substring(idx1 + 2, idx2).trim();
                                String msg = message.substring(idx2 + 1).trim();
                                saveGroupMessageToLocal(lastWin.groupName, sender, msg);
                            }
                        } catch (javax.swing.text.BadLocationException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        if (message.startsWith("GROUPS:")) {
            System.out.println("[DEBUG][ChatUIGroup] 处理GROUPS消息: " + message);
            String[] groups = message.substring(7).split(",");
            myGroups = new ArrayList<>();
            for (String g : groups) {
                if (!g.trim().isEmpty()) myGroups.add(g.trim());
            }
            System.out.println("[DEBUG][ChatUIGroup] myGroups updated: " + myGroups);
            // 新增同步allGroups（假设服务器返回所有群聊列表，格式ALLGROUPS:群1,群2,...）
            SwingUtilities.invokeLater(() -> {
                System.out.println("[DEBUG][ChatUIGroup] invokeLater showGroupList for myGroups");
                showGroupList();
            });
        } else if (message.startsWith("ALLGROUPS:")) {
            System.out.println("[DEBUG][ChatUIGroup] 处理ALLGROUPS消息: " + message);
            String[] groups = message.substring(10).split(",");
            allGroups = new ArrayList<>();
            for (String g : groups) {
                if (!g.trim().isEmpty()) allGroups.add(g.trim());
            }
            System.out.println("[DEBUG][ChatUIGroup] allGroups updated: " + allGroups);
            SwingUtilities.invokeLater(() -> {
                System.out.println("[DEBUG][ChatUIGroup] invokeLater showGroupList for allGroups");
                showGroupList();
            });
        } else if (message.startsWith("SUCCESS: 创建群聊成功")) {
            System.out.println("[DEBUG][ChatUIGroup] 创建群聊成功: " + message);
            JOptionPane.showMessageDialog(parentFrame, message, "小组创建", JOptionPane.INFORMATION_MESSAGE);
            // 自动刷新小组列表
            if (lastSGListGroupName != null) {
                client.sendMessage("/sg_list " + lastSGListGroupName);
            }
        } else if (message.startsWith("ERROR: 小组创建失败")) {
            System.out.println("[DEBUG][ChatUIGroup] 小组创建失败: " + message);
            JOptionPane.showMessageDialog(parentFrame, message, "小组创建失败", JOptionPane.ERROR_MESSAGE);
        } else if (message.startsWith("[群聊]")) {
            System.out.println("[DEBUG][ChatUIGroup] 收到群聊消息: " + message);
            // 群聊消息分发到���应窗口
            int idx1 = message.indexOf("] ");
            int idx2 = message.indexOf("|", idx1 + 2);
            int idx3 = message.indexOf(":", idx2 + 1);
            if (idx1 != -1 && idx2 != -1 && idx3 != -1) {
                String group = message.substring(idx1 + 2, idx2).trim();
                String sender = message.substring(idx2 + 1, idx3).trim();
                String msg = message.substring(idx3 + 1).trim();
                GroupChatWindow win = groupChats.get(group);
                if (win == null || !win.isDisplayable()) {
                    win = new GroupChatWindow(group);
                    groupChats.put(group, win);
                }
                win.appendMessage(sender + ": " + msg);
            }
        } else if (message.startsWith("FILE_NOTIFY:")) {
            System.out.println("[DEBUG][ChatUIGroup] 收到文件通知: " + message);
            // 文件通知分发到群聊或小组窗口
            String[] parts = message.split(":");
            if (parts.length >= 7 && "group".equals(parts[6])) {
                String fileId = parts[1];
                String fileName = parts[2];
                long fileSize = Long.parseLong(parts[3]);
                String sender = parts[4];
                String groupName = parts[5];
                GroupChatWindow win = groupChats.get(groupName);
                if (win == null || !win.isDisplayable()) {
                    win = new GroupChatWindow(groupName);
                    groupChats.put(groupName, win);
                }
                win.setVisible(true);
                win.toFront();
                win.showFileReceiveDialog(fileId, fileName, fileSize, sender);
            } else if (parts.length >= 8 && "subgroup".equals(parts[7])) {
                // 小组文件通知格式: FILE_NOTIFY:fileId:fileName:fileSize:sender:groupName:subGroupId:subgroup
                String fileId = parts[1];
                String fileName = parts[2];
                long fileSize = Long.parseLong(parts[3]);
                String sender = parts[4];
                String groupName = parts[5];
                String subGroupId = parts[6];
                String key = groupName + "#" + subGroupId;
                SubGroupUI.SubGroupChatWindow win = subGroupUI.subGroupChats.get(key);
                if (win == null || !win.isDisplayable()) {
                    win = subGroupUI.new SubGroupChatWindow(groupName, subGroupId);
                    subGroupUI.subGroupChats.put(key, win);
                }
                win.setVisible(true);
                win.toFront();
                win.showFileReceiveDialog(fileId, fileName, fileSize, sender);
            }
        } else if (message.startsWith("IMAGE_NOTIFY:") && message.endsWith(":group")) {
            System.out.println("[DEBUG][ChatUIGroup] 收到群聊图片通知: " + message);
            // 群聊图片通知
            String[] parts = message.split(":");
            if (parts.length >= 6) {
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                String sender = parts[3];
                String imagePath = parts[4];
                // 优化群名解析：优先从 imagePath 提取
                String groupName = null;
                for (String g : myGroups) {
                    if (imagePath.contains("group_" + g + "#") || imagePath.contains("group_" + g + "/") || imagePath.contains("group_" + g + ".")) {
                        groupName = g;
                        break;
                    } else if (imagePath.contains("group_" + g)) {
                        groupName = g;
                    }
                }
                if (groupName == null) groupName = "未知群聊";
                GroupChatWindow win = groupChats.get(groupName);
                if (win == null || !win.isDisplayable()) {
                    win = new GroupChatWindow(groupName);
                    groupChats.put(groupName, win);
                }
                win.setVisible(true);
                win.toFront();
                win.appendImageMessage(sender, fileName, imagePath);
                win.saveGroupMessageToLocal(sender, "[图片] " + fileName + " " + imagePath);
            }
        } else if (message.startsWith("SG_LIST:")) {
            System.out.println("[DEBUG][ChatUIGroup] 收到SG_LIST消息: " + message);
            // 小组列表信息格式：SG_LIST:小组ID|小组名|成员1,成员2;小组ID2|小组名2|成员1,...
            String data = message.substring(8);
            String groupNameKey = lastSGListGroupName;
            // 修正：如果lastSGListGroupName为null，尝试从SG_LIST内容中解析群名
            if (groupNameKey == null) {
                // 尝试从data中解析群名（假设格式为：群��:小组ID|小组名|成员1,成员2;...）
                int idx = data.indexOf(":");
                if (idx > 0) {
                    groupNameKey = data.substring(0, idx);
                    data = data.substring(idx + 1);
                }
            }
            System.out.println("[ChatUIGroup] lastSGListGroupName=" + groupNameKey);
            if (groupNameKey != null) {
                System.out.println("[ChatUIGroup] 更新小组列表: " + groupNameKey + ", " + data);
                subGroupUI.onSubGroupListReceived(groupNameKey, data);
            }
        } else if (message.startsWith("[小组]")) {
            System.out.println("[DEBUG][ChatUIGroup] 收到小组消息: " + message);
            // 小组消息格式：[小组] 群名|小组名|发送者: 消消息内容
            int idx1 = message.indexOf("] ");
            int idx2 = message.indexOf("|", idx1 + 2);
            int idx3 = message.indexOf("|", idx2 + 1);
            int idx4 = message.indexOf(":", idx3 + 1);
            if (idx1 != -1 && idx2 != -1 && idx3 != -1 && idx4 != -1) {
                String groupName = message.substring(idx1 + 2, idx2).trim();
                String subGroupName = message.substring(idx2 + 1, idx3).trim();
                String sender = message.substring(idx3 + 1, idx4).trim();
                String msg = message.substring(idx4 + 1).trim();
                // 通过群聊名称和小组名查找窗口key
                String key = null;
                for (String g : myGroups) {
                    if (g.equals(groupName)) {
                        key = g + "#" + subGroupName;
                        break;
                    }
                }
                if (key == null) key = groupName + "#" + subGroupName;
                SubGroupChatWindow win = subGroupChats.get(key);
                if (win == null || !win.isDisplayable()) {
                    win = new SubGroupChatWindow(groupName, subGroupName);
                    subGroupChats.put(key, win);
                }
                win.appendMessage(sender + ": " + msg);
            }
        } else if (message.startsWith("SUCCESS: 小组创建成功")) {
            System.out.println("[DEBUG][ChatUIGroup] 小组创建成功: " + message);
            JOptionPane.showMessageDialog(parentFrame, message, "小组创建", JOptionPane.INFORMATION_MESSAGE);
            // 自动刷新小组列表
            if (lastSGListGroupName != null) {
                client.sendMessage("/sg_list " + lastSGListGroupName);
            }
        } else if (message.startsWith("SUCCESS: 加入小组成功") || message.startsWith("SUCCESS: 退出小组成功") || message.startsWith("SUCCESS: 邀请成功")) {
            System.out.println("[DEBUG][ChatUIGroup] 小组操作成功: " + message);
            JOptionPane.showMessageDialog(parentFrame, message);
            // 新增：成功加入/退出/邀请后刷新小组列表
            if (lastSGListGroupName != null) {
                client.sendMessage("/sg_list " + lastSGListGroupName);
            }
            // 加入/退出小组后，强制刷新群聊列表
            client.sendMessage("/glist");
        } else if (message.startsWith("ERROR:")) {
            System.out.println("[DEBUG][ChatUIGroup] 错误消息: " + message);
            JOptionPane.showMessageDialog(parentFrame, message, "错误", JOptionPane.ERROR_MESSAGE);
        } else if (message.startsWith("SYSTEM: 你被邀请加入群")) {
            System.out.println("[DEBUG][ChatUIGroup] 被邀请加入群: " + message);
            JOptionPane.showMessageDialog(parentFrame, message, "小组邀请", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ���增：群聊窗口打开方法，供外部调用
    public void openGroupChatWindow(String groupName) {
        GroupChatWindow win = groupChats.get(groupName);
        if (win == null || !win.isDisplayable()) {
            win = new GroupChatWindow(groupName);
            groupChats.put(groupName, win);
        }
        win.setTitle("群聊 - " + groupName);
        win.setLocationRelativeTo(null);
        win.setVisible(true);
        win.toFront();
        // 新增：打开群聊窗口时请求服务器最新群聊历史消息
        client.sendMessage("/hg " + groupName);
    }

    // 新增：返回当前已打开的群聊窗口数
    public int getOpenChatWindowCount() {
        int openCount = 0;
        for (GroupChatWindow win : groupChats.values()) {
            if (win != null && win.isDisplayable()) openCount++;
        }
        for (SubGroupChatWindow win : subGroupChats.values()) {
            if (win != null && win.isDisplayable()) openCount++;
        }
        return openCount;
    }
}
