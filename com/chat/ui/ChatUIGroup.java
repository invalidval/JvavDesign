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

public class ChatUIGroup implements MessageObserver {
    private JPanel groupPanel;
    private JPanel groupListPanel;
    private Client client;
    private JFrame parentFrame;
    private String currentUser;
    private Map<String, GroupChatWindow> groupChats = new ConcurrentHashMap<>();
    private List<String> myGroups = new ArrayList<>();
    private Map<String, List<String>> groupMembers = new HashMap<>();
    private ChatWindowLimitProvider limitProvider;

    public ChatUIGroup(Client client, JFrame parentFrame, String currentUser, ChatWindowLimitProvider limitProvider) {
        this.client = client;
        this.parentFrame = parentFrame;
        this.currentUser = currentUser;
        this.limitProvider = limitProvider; // 接收 ChatWindowLimitProvider 实例
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
        groupListPanel.removeAll();
        if (myGroups.isEmpty()) {
            JLabel emptyLabel = new JLabel("暂无群聊");
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            groupListPanel.add(emptyLabel);
        } else {
            for (String group : myGroups) {
                JPanel groupCard = new JPanel(new BorderLayout());
                groupCard.setMaximumSize(new Dimension(600, 40));
                groupCard.setPreferredSize(new Dimension(600, 40));
                groupCard.setMinimumSize(new Dimension(600, 40));
                groupCard.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                groupCard.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel groupLabel = new JLabel(group);
                JButton chatButton = new JButton("进入群聊");
                chatButton.addActionListener(e -> openGroupChatWindow(group));
                groupCard.add(groupLabel, BorderLayout.CENTER);
                groupCard.add(chatButton, BorderLayout.EAST);
                groupListPanel.add(groupCard);
                groupListPanel.add(Box.createVerticalStrut(6));
            }
        }
        groupListPanel.revalidate();
        groupListPanel.repaint();
    }

    // 返回当前已打开的群聊窗口数
    public int getOpenChatWindowCount() {
        int openCount = 0;
        for (GroupChatWindow win : groupChats.values()) {
            if (win != null && win.isDisplayable()) openCount++;
        }
        return openCount;
    }

    public void openGroupChatWindow(String groupName) {
        // 统一计数所有聊天窗口（私聊+群聊）
        int max = limitProvider.getMaxChatWindows();
        int totalOpen = limitProvider.getCurrentOpenChatWindowCount();
        if (!groupChats.containsKey(groupName) && totalOpen >= max) {
            JOptionPane.showMessageDialog(parentFrame, "已达到最大聊天窗口数(" + max + ")，请先关闭其他窗口！", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        GroupChatWindow win = groupChats.get(groupName);
        if (win == null || !win.isDisplayable()) {
            win = new GroupChatWindow(groupName);
            win.setSize(600, 400);
            groupChats.put(groupName, win);
        }
        win.setTitle("群聊 - " + groupName);
        win.setLocationRelativeTo(null);
        win.setVisible(true);
        win.toFront();
        // 新增：加载未读图片消息（反射兼容ChatApp类型）
        if (parentFrame.getClass().getSimpleName().equals("ChatApp")) {
            try {
                java.lang.reflect.Method pollMethod = parentFrame.getClass().getMethod("pollUnreadImages", String.class, boolean.class);
                java.util.List<?> imgs = (java.util.List<?>) pollMethod.invoke(parentFrame, groupName, true);
                for (Object img : imgs) {
                    java.lang.reflect.Field senderField = img.getClass().getDeclaredField("sender");
                    java.lang.reflect.Field fileNameField = img.getClass().getDeclaredField("fileName");
                    java.lang.reflect.Field imagePathField = img.getClass().getDeclaredField("imagePath");
                    senderField.setAccessible(true);
                    fileNameField.setAccessible(true);
                    imagePathField.setAccessible(true);
                    String sender = (String) senderField.get(img);
                    String fileName = (String) fileNameField.get(img);
                    String imagePath = (String) imagePathField.get(img);
                    win.appendImageMessage(sender, fileName, imagePath);
                }
            } catch (Exception ex) {
                // 反射失败忽略
            }
        }
    }

    @Override
    public void onMessageReceived(String message) {
        if (message == null) return;
        if (message.startsWith("GROUPS:")) {
            // 服务器返回群列表，格式：GROUPS:群1,群2,...
            String[] groups = message.substring(7).split(",");
            myGroups = new ArrayList<>();
            for (String g : groups) {
                if (!g.trim().isEmpty()) myGroups.add(g.trim());
            }
            showGroupList();
        } else if (message.startsWith("GROUPMEMBERS:")) {
            // 服务器返回群成员，格式：GROUPMEMBERS:群名:成员1,成员2,...
            String[] arr = message.split(":", 3);
            if (arr.length == 3) {
                String group = arr[1];
                List<String> members = Arrays.asList(arr[2].split(","));
                groupMembers.put(group, members);
            }
        } else if (message.startsWith("[群聊]")) {
            // 格式: [群聊] 群名|发送者: 消息
            int idx1 = message.indexOf("] ");
            int idx2 = message.indexOf("|", idx1 + 2);
            int idx3 = message.indexOf(":", idx2 + 1);
            if (idx1 != -1 && idx2 != -1 && idx3 != -1) {
                String group = message.substring(idx1 + 2, idx2).trim();
                String sender = message.substring(idx2 + 1, idx3).trim();
                String msg = message.substring(idx3 + 1).trim();
                saveGroupMessageToLocal(group, sender, msg); // 修正：传入sender和msg
                GroupChatWindow win = groupChats.get(group);
                if (win != null && win.isDisplayable()) {
                    win.appendMessage(sender + ": " + msg);
                    win.setVisible(true);
                }
                // 不再自动弹出聊天窗口
            }
        } else if (message.startsWith("SUCCESS: 创建群聊成功")) {
            JOptionPane.showMessageDialog(parentFrame, message);
            requestGroupList();
        } else if (message.startsWith("SUCCESS: 加入群聊成功")) { // 新增处理加入群聊成功的消息
            JOptionPane.showMessageDialog(parentFrame, message);
            requestGroupList(); // 刷新群聊列表
        } else if (message.startsWith("ERROR: 加入群聊失败")) { // 新增处理加入群聊失败的消息
            JOptionPane.showMessageDialog(parentFrame, message, "错误", JOptionPane.ERROR_MESSAGE);
        } else if (message.startsWith("FILE_NOTIFY:")) {
            String[] parts = message.split(":");
            String fileId = parts[1];
            String fileName = parts[2];
            long fileSize = Long.parseLong(parts[3]);
            String sender = parts[4];
            String targetId = parts[5]; // 群名

            // 保存文件记录到历史
            FileRecord record = new FileRecord(fileId, fileName, fileSize, sender, targetId, true, System.currentTimeMillis());
            FileHistoryXmlManager.addRecord(currentUser,true ,sender, record);

            // 打开或创建群聊窗口
            GroupChatWindow win = groupChats.computeIfAbsent(targetId, gn -> {
                GroupChatWindow w = new GroupChatWindow(gn);
                w.setVisible(true);
                return w;
            });

            // 在群聊窗口中显示文件接收提示
            win.showFileReceiveDialog(fileId, fileName, fileSize, sender);
        } else if (message.startsWith("IMAGE_NOTIFY:")) {
            // 群聊图片通知格式: IMAGE_NOTIFY:文件名:文件大小:发送者:图片路径:group
            String[] parts = message.split(":");
            if (parts.length >= 6 && "group".equals(parts[5])) {
                String fileName = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                String sender = parts[3];
                String imagePath = parts[4];
                String groupName = null;
                for (String g : myGroups) {
                    if (imagePath.contains(g)) {
                        groupName = g;
                        break;
                    }
                }
                if (groupName == null) groupName = "未知群聊";
                saveGroupMessageToLocal(groupName, sender, "[图片] " + fileName + " " + imagePath); // 修正：带上sender
                // 不再自动弹出聊天窗口，仅在消息栏提示
                if (parentFrame.getClass().getSimpleName().equals("ChatApp")) {
                    try {
                        java.lang.reflect.Field sessionLastMsgField = parentFrame.getClass().getDeclaredField("sessionLastMsg");
                        java.lang.reflect.Field sessionIsGroupField = parentFrame.getClass().getDeclaredField("sessionIsGroup");
                        java.lang.reflect.Field messageListPanelField = parentFrame.getClass().getDeclaredField("messageListPanel");
                        sessionLastMsgField.setAccessible(true);
                        sessionIsGroupField.setAccessible(true);
                        messageListPanelField.setAccessible(true);
                        java.util.Map<String, String> sessionLastMsg = (java.util.Map<String, String>) sessionLastMsgField.get(parentFrame);
                        java.util.Map<String, Boolean> sessionIsGroup = (java.util.Map<String, Boolean>) sessionIsGroupField.get(parentFrame);
                        Object messageListPanel = messageListPanelField.get(parentFrame);
                        sessionLastMsg.put(groupName, sender + ": [图片]");
                        sessionIsGroup.put(groupName, true);
                        if (messageListPanel != null) {
                            java.lang.reflect.Method refreshMethod = messageListPanel.getClass().getMethod("refresh");
                            refreshMethod.invoke(messageListPanel);
                        }
                    } catch (Exception ex) {
                        // 反射失败忽略
                    }
                }
                // 可选：如需保存未读图片消息，可扩展此处逻辑
            } else {
                // ...existing code...
            }
        }
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
            sendImageButton.addActionListener(e -> sendImageAction());
            inputPanel.add(sendImageButton, BorderLayout.WEST);

            chatPanel.add(scrollPane, BorderLayout.CENTER);
            chatPanel.add(inputPanel, BorderLayout.SOUTH);

            tabbedPane.addTab("聊天", chatPanel);
            tabbedPane.addTab("文件", fileListPanel);
            add(tabbedPane, BorderLayout.CENTER);

            // 可扩展：显示群成员列表
            //新增文件���传功能
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

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttonPanel.add(fileButton);
            buttonPanel.add(historyButton);
            // 修改inputPanel的添加方式
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(buttonPanel, BorderLayout.NORTH);
            bottomPanel.add(inputPanel, BorderLayout.CENTER);

            add(bottomPanel, BorderLayout.SOUTH);

            // 窗口关闭时清理资源
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    groupChats.remove(groupName);
                    super.windowClosing(e);
                }
            });
        }

        private void sendMessage() {
            String message = inputField.getText().trim();
            if (!message.isEmpty()) {
                client.sendMessage("/gs " + groupName + " " + message);
                // 删除本地appendMessage("我: " + message); 只由onMessageReceived处理显示
                inputField.setText("");
            }
        }

        public void appendMessage(String msg) {
            try {
                javax.swing.text.Document doc = chatArea.getDocument();
                doc.insertString(doc.getLength(), msg + "\n", null);
            } catch (javax.swing.text.BadLocationException e) {
                e.printStackTrace();
            }
        }

        // 新增：群聊图片发送动作
        private void sendImageAction() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("选择��发送的图片");
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                if (com.chat.NewFunctions.image.ImageManager.isImageFile(file)) {
                    try {
                        int imagePort = 18989; // 群聊图片端口
                        com.chat.NewFunctions.image.ImageManager.sendImageToServer(
                                client.getHost(), imagePort, file, groupName, currentUser + ":group");
                        try {
                            javax.swing.text.Document doc = chatArea.getDocument();
                            doc.insertString(doc.getLength(), "[图片已发送: " + file.getName() + "]\n", null);
                        } catch (javax.swing.text.BadLocationException e) {
                            e.printStackTrace();
                        }
                    } catch (Exception ex) {
                        try {
                            javax.swing.text.Document doc = chatArea.getDocument();
                            doc.insertString(doc.getLength(), "[图片发送失败: " + file.getName() + "]\n", null);
                        } catch (javax.swing.text.BadLocationException e) {
                            e.printStackTrace();
                        }
                        JOptionPane.showMessageDialog(this, "图片发送失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "请选择图片文件（jpg/png/gif/bmp）", "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        }

        // 新增：群聊图片接收与查看
        public void showImageReceiveDialog(String fileName, long fileSize, String sender, String imagePath) {
            // 直接插入图片缩略图，不弹窗，仿微信风格
            appendImageMessage(sender, fileName, imagePath);
        }

        // 群聊文件接收与下载弹窗
        public void showFileReceiveDialog(String fileId, String fileName, long fileSize, String sender) {
            appendMessage(sender + " 发送了文件: " + fileName);
            int option = JOptionPane.showConfirmDialog(this,
                    sender + " 发送了文件: " + fileName + "\n是否下载?",
                    "收到群文件",
                    JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
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
                                public void onProgress(int percentage) {}
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

        // 聊天窗口插入图片消息（自动加载并显示图片缩略图，可点击查看大图）
        private final Map<Integer, String> imageOffsetMap = new HashMap<>(); // offset -> imagePath
        private boolean imageMouseListenerAdded = false;
        public void appendImageMessage(String sender, String fileName, String imagePath) {
            String tempDir = System.getProperty("java.io.tmpdir");
            String localPath = tempDir + File.separator + fileName;
            new Thread(() -> {
                try {
                    File imageFile = new File(imagePath);
                    if (imageFile.exists()) {
                        displayImage(sender, fileName, imagePath);
                    } else {
                        String realSender = sender;
                        if (realSender.endsWith(":group")) {
                            realSender = realSender.substring(0, realSender.length() - 6);
                        }
                        try (java.net.Socket sock = new java.net.Socket(client.getHost(), 18989);
                             java.io.DataOutputStream out = new java.io.DataOutputStream(sock.getOutputStream());
                             java.io.DataInputStream in = new java.io.DataInputStream(sock.getInputStream())) {
                            String cmd = "/IMAGE_DOWNLOAD " + fileName + " " + realSender + " " + currentUser;
                            out.writeUTF(cmd);
                            out.flush();
                            String resp = in.readUTF();
                            if (!"IMAGE_DATA".equals(resp)) {
                                SwingUtilities.invokeLater(() -> appendMessage("[图片下载失败: " + resp + "]"));
                                return;
                            }
                            String recvFileName = in.readUTF();
                            long recvFileSize = in.readLong();
                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(localPath)) {
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
                        doc.insertString(doc.getLength(), "[图片加载失败]\n", null);
                        return;
                    }
                    // 动态缩略图最大边长，取chatArea宽度的1/3，最小100，最大300
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
                    // 记录图片offset和路径
                    imageOffsetMap.put(insertPos, imagePath);
                    doc.insertString(doc.getLength(), "\n", null);
                    chatArea.setCaretPosition(doc.getLength());
                    // 只添加一次MouseListener
                    if (!imageMouseListenerAdded) {
                        chatArea.addMouseListener(new java.awt.event.MouseAdapter() {
                            public void mouseClicked(java.awt.event.MouseEvent evt) {
                                int pos = chatArea.viewToModel2D(evt.getPoint());
                                // 查找最近的图片offset
                                for (Map.Entry<Integer, String> entry : imageOffsetMap.entrySet()) {
                                    int offset = entry.getKey();
                                    if (pos == offset) {
                                        String imgPath = entry.getValue();
                                        // 新的自适应弹窗
                                        JDialog dialog = new JDialog(GroupChatWindow.this, "图片预览", true);
                                        JLabel bigLabel = new JLabel();
                                        JScrollPane pane = new JScrollPane(bigLabel);
                                        dialog.setContentPane(pane);
                                        // 初始尺寸
                                        dialog.setSize(600, 600);
                                        dialog.setLocationRelativeTo(GroupChatWindow.this);
                                        // 动态缩放图片方法
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
                                        // 监听弹窗和滚动面板尺寸变化
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
                                        // 初始显示
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

        // 加载本地群聊聊天记录
        private void loadLocalGroupChatHistory() {
            String fileName = "chat_group_" + groupName + ".txt";
            File file = new File(System.getProperty("user.home") + File.separator + "ChatLocalHistory", fileName);
            if (file.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 格式: [时间] 发送方ID: 消息内容
                        int idx1 = line.indexOf("] ");
                        int idx2 = line.indexOf(":", idx1 + 2);
                        if (idx1 != -1 && idx2 != -1) {
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
                            chatArea.getDocument().insertString(chatArea.getDocument().getLength(), line + "\n", null);
                        } else {
                            chatArea.getDocument().insertString(chatArea.getDocument().getLength(), line + "\n", null);
                        }
                    }
                } catch (Exception e) {
                    // 忽略读取异常
                }
            }
        }
    }
}

