package com.chat.ui;

import com.chat.client.Client;
import com.chat.file.FileTransferManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.chat.NewFunctions.audio.VoiceChatManager;
import javax.sound.sampled.LineUnavailableException;

public class SubGroupUI {
    private Client client;
    private JFrame parentFrame;
    private String currentUser;
    private JPanel mainPanel;
    // 小组聊天窗口管理
    Map<String, SubGroupChatWindow> subGroupChats = new ConcurrentHashMap<>();
    // 小组信息缓存
    private Map<String, String> subGroupInfoCache = new HashMap<>();
    private Map<String, Map<String, Set<String>>> subGroupMemberMap = new HashMap<>();
    private String lastSGListGroupName = null;
    private Map<String, JPanel> groupSubGroupPanelMap = new HashMap<>();
    // 新增：记录每个群的小组管理对话框
    private Map<String, JDialog> groupSubGroupDialogMap = new HashMap<>();
    // 新增：小组语音管理器
    private final VoiceChatManager subGroupVoiceChatManager = new VoiceChatManager();

    public SubGroupUI(Client client, JFrame parentFrame, String currentUser) {
        this.client = client;
        this.parentFrame = parentFrame;
        this.currentUser = currentUser;
        this.mainPanel = new JPanel(new BorderLayout());
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    public Client getClient() {
        return client;
    }

    public JFrame getParentFrame() {
        return parentFrame;
    }

    public String getCurrentUser() {
        return currentUser;
    }

    // ================== 小组信息缓存与唯一性校验相关 ==================
    // 小组聊天窗口管理、缓存、面板等字段已在类成员区声明

    // 打开小组聊天窗口
    public void openSubGroupChatWindow(String groupName, String subGroupId) {
        // 先关闭小组管理对话框
        JDialog dialog = groupSubGroupDialogMap.get(groupName);
        if (dialog != null) {
            dialog.dispose();
            groupSubGroupDialogMap.remove(groupName);
        }
        String key = groupName + "#" + subGroupId;
        SubGroupChatWindow win = subGroupChats.get(key);
        if (win == null || !win.isDisplayable()) {
            win = new SubGroupChatWindow(groupName, subGroupId);
            win.setSize(600, 400);
            subGroupChats.put(key, win);
        }
        win.setTitle("小组聊天 - " + groupName + " | 小组ID:" + subGroupId);
        win.setLocationRelativeTo(null);
        win.setVisible(true);
        win.toFront();
        win.requestFocus();
    }

    // 小组聊天窗口
    class SubGroupChatWindow extends JFrame {
        private JTextPane chatArea;
        private JTextField inputField;
        private String groupName, subGroupId;
        private JTabbedPane tabbedPane;
        private JPanel chatPanel;
        private FileListPanel fileListPanel;
        // 图片消息offset映射（offset -> imagePath）
        private final Map<Integer, String> imageOffsetMap = new HashMap<>();
        private boolean imageMouseListenerAdded = false;
        // 新增：语音通话相关UI组件
        private JButton voiceCallButton;
        private JButton stopVoiceCallButton;
        private boolean isVoiceCalling = false;
        private String voiceSessionId;
        private int defaultVoicePort = 20011; // 小组默认端口

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
            // 加载本地小组聊天记录
            loadLocalSubGroupChatHistory();
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
                        saveSubGroupMessageToLocal(currentUser, "[文件] " + selectedFile.getName());
                        JOptionPane.showMessageDialog(this, "文件发送成功!");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(this,
                                "文件发送失败: " + ex.getMessage(),
                                "错误",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            // 历史消息按钮
            JButton historyButton = new JButton("查看历史消息");
            historyButton.addActionListener(e -> {
                ChatUIHistory historyPanel = new ChatUIHistory(client, this, currentUser, "subgroup", groupName + "#" + subGroupId);
                JDialog dialog = new JDialog(this, "小组历史消息 - " + groupName + " | 小组ID:" + subGroupId, true);
                dialog.setContentPane(historyPanel.getPanel());
                dialog.setSize(600, 400);
                dialog.setLocationRelativeTo(this);
                dialog.setVisible(true);
            });
            // 新增：语音通话按钮
            voiceCallButton = new JButton("语音通话");
            stopVoiceCallButton = new JButton("挂断");
            stopVoiceCallButton.setEnabled(false);

            voiceCallButton.addActionListener(e -> startVoiceCall());
            stopVoiceCallButton.addActionListener(e -> stopVoiceCall());

            JPanel voicePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            voicePanel.add(voiceCallButton);
            voicePanel.add(stopVoiceCallButton);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            buttonPanel.add(fileButton);
            buttonPanel.add(historyButton);

            // 新增“我的图片”按钮
            JButton myImagesButton = new JButton("我的图片");
            myImagesButton.addActionListener(e -> openMySubGroupImagesFolder()); // 绑定事件
            buttonPanel.add(myImagesButton); // 添加到按钮面板

            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.add(voicePanel, BorderLayout.NORTH);
            bottomPanel.add(buttonPanel, BorderLayout.CENTER);
            bottomPanel.add(inputPanel, BorderLayout.SOUTH);

            add(bottomPanel, BorderLayout.SOUTH);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    subGroupChats.remove(groupName + "#" + subGroupId);
                    stopVoiceCall();
                }
            });
        }

        // 新增：启动语音通话
        private void startVoiceCall() {
            if (isVoiceCalling) return;
            try {
                subGroupVoiceChatManager.initAudioDevices();
                voiceSessionId = "subgroup_" + groupName + "_" + subGroupId;
                String[] options = {"作为主叫（对方需先点被叫）", "作为被叫（先点，等待主叫连接）"};
                int choice = JOptionPane.showOptionDialog(this, "请选择通话角色：", "语音通话",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                if (choice == 0) {
                    String host = JOptionPane.showInputDialog(this, "请输入对方IP（本机可填127.0.0.1）", "127.0.0.1");
                    subGroupVoiceChatManager.connectToPeer(host, defaultVoicePort);
                } else if (choice == 1) {
                    JOptionPane.showMessageDialog(this, "请等待对方发起连接...", "提示", JOptionPane.INFORMATION_MESSAGE);
                    new Thread(() -> {
                        try {
//                            subGroupVoiceChatManager.startServer(defaultVoicePort);
                        } catch (Exception ex) {
                            SwingUtilities.invokeLater(() -> appendMessage("[语音服务端启动失败] " + ex.getMessage()));
                        }
                    }).start();
                } else {
                    return;
                }
                subGroupVoiceChatManager.startSession(voiceSessionId);
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
                subGroupVoiceChatManager.stopSession(voiceSessionId);
                subGroupVoiceChatManager.closeSocket();
                appendMessage("[语音通话已挂断]");
            } catch (Exception ex) {
                appendMessage("[挂断失败] " + ex.getMessage());
            }
            isVoiceCalling = false;
            voiceCallButton.setEnabled(true);
            stopVoiceCallButton.setEnabled(false);
        }

        private void sendMessage() {
            String msg = inputField.getText().trim();
            if (!msg.isEmpty()) {
                client.sendMessage("/sg_msg " + groupName + " " + subGroupId + " " + msg);
                appendMessage(currentUser + ": " + msg);
                saveSubGroupMessageToLocal(currentUser, msg);
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
                        // 路径格式：C:\Users\<用户名>\ChatLocalHistory\images\SubGroupsImage\发送方用户名_to_群聊名称#小组ID\
                        String userHome = System.getProperty("user.home");
                        String imageSaveDir = userHome + File.separator + "ChatLocalHistory" + File.separator + "images" + File.separator + "SubGroupsImage" + File.separator + currentUser + "_to_" + groupName + "#" + subGroupId;
                        File saveDir = new File(imageSaveDir);
                        if (!saveDir.exists()) saveDir.mkdirs();
                        File destFile = new File(saveDir, file.getName());
                        java.nio.file.Files.copy(file.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        String groupKey = groupName + "#" + subGroupId;
                        com.chat.NewFunctions.image.ImageManager.sendImageToServer(
                                client.getHost(), imagePort, destFile, groupKey, currentUser + ":group");
                        appendImageMessage(currentUser, destFile.getName(), destFile.getAbsolutePath());
                        saveSubGroupMessageToLocal(currentUser, "[图片] " + destFile.getName() + " " + destFile.getAbsolutePath());
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
            } catch (javax.swing.text.BadLocationException e) {
                e.printStackTrace();
            }
        }
        // 图片消息插入与点击大图预览
        public void appendImageMessage(String sender, String fileName, String imagePath) {
            SwingUtilities.invokeLater(() -> {
                try {
                    // 1. 显示发送图片的时间和发送者
                    String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format
                            (new java.util.Date());
                    javax.swing.text.StyledDocument doc = chatArea.getStyledDocument();
                    doc.insertString(doc.getLength(), "[" + time + "] " + sender + " 发送了图片: ", null);
                    // 2. 加载原始图片
                    ImageIcon icon = new ImageIcon(imagePath);
                    if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
                        doc.insertString(doc.getLength(), "[图片加载失败]\n", null);
                        return;
                    }
                    // 3. 计算缩略图尺寸（最大边不超过maxThumb）
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
                    // 4. 生成缩略图并插入到聊天区
                    Image img = icon.getImage().getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
                    ImageIcon thumbIcon = new ImageIcon(img);
                    javax.swing.text.Style style = chatArea.addStyle("imageStyle" + System.nanoTime(), null);
                    javax.swing.text.StyleConstants.setIcon(style, thumbIcon);
                    int insertPos = doc.getLength();
                    doc.insertString(insertPos, "ignored", style);
                    imageOffsetMap.put(insertPos, imagePath);
                    doc.insertString(doc.getLength(), "\n", null);
                    chatArea.setCaretPosition(doc.getLength());
                    // 5. 只添加一次鼠标监听器，实现点击图片预览大图
                    if (!imageMouseListenerAdded) {
                        chatArea.addMouseListener(new java.awt.event.MouseAdapter() {
                            public void mouseClicked(java.awt.event.MouseEvent evt) {
                                int pos = chatArea.viewToModel2D(evt.getPoint());
                                for (Map.Entry<Integer, String> entry : imageOffsetMap.entrySet()) {
                                    int offset = entry.getKey();
                                    if (pos == offset) {
                                        String imgPath = entry.getValue();
                                        // 弹出对话框显示大图
                                        JDialog dialog = new JDialog(SubGroupChatWindow.this, "图片预览", true);
                                        JLabel bigLabel = new JLabel();
                                        JScrollPane pane = new JScrollPane(bigLabel);
                                        dialog.setContentPane(pane);
                                        dialog.setSize(600, 600);
                                        dialog.setLocationRelativeTo(SubGroupChatWindow.this);
                                        // 预览窗口自适应缩放图片
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
                                        // 监听窗口和滚动面��大小变化，动态缩放图片
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
                                        updateImage.run(); // 初始显示
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
        private void openMySubGroupImagesFolder() {
            try {
                String userHome = System.getProperty("user.home");
                // 子群图片保存路径应与发送图片时一致
                String imagesDirPath = userHome + File.separator + "ChatLocalHistory" + File.separator + "images" + File.separator + "SubGroupsImage" + File.separator + currentUser + "_to_" + groupName + "#" + subGroupId;
                File imagesDir = new File(imagesDirPath);
                if (!imagesDir.exists() || !imagesDir.isDirectory()) {
                    JOptionPane.showMessageDialog(parentFrame, "未找到图片文件夹: " + imagesDirPath, "文件夹不存在", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                Desktop.getDesktop().open(imagesDir);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(parentFrame, "打开文件夹时发生错误: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
        // 加载本���小组聊天历史
        private void loadLocalSubGroupChatHistory() {
            String fileName = "chat_subgroup_" + groupName + "_" + subGroupId + ".txt";
            File file = new File(System.getProperty("user.home") + File.separator
                    + "ChatLocalHistory", fileName);
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
        // 保存小组消息到本地
        private void saveSubGroupMessageToLocal(String sender, String message) {
            String dirPath = System.getProperty("user.home") + File.separator + "ChatLocalHistory";
            File dir = new File(dirPath);
            if (!dir.exists()) dir.mkdirs();
            String fileName = "chat_subgroup_" + groupName + "_" + subGroupId + ".txt";
            File file = new File(dir, fileName);
            String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String line = String.format("[%s] %s: %s", time, sender, message);
            try (java.io.FileWriter fw = new java.io.FileWriter(file, true)) {
                fw.write(line + "\n");
            } catch (Exception e) {
                // 忽略写入异常
            }
        }
        // 可选：图片接收与显示、文件接收等功能可后续补充
        // 新增：小组文件接收弹窗和下载功能
        public void showFileReceiveDialog(String fileId, String fileName, long fileSize, String sender) {
            appendMessage(sender + " 发送了文件: " + fileName);
            int option = JOptionPane.showConfirmDialog(this,
                    sender + " 发送了文件: " + fileName + "\n是否下载?",
                    "收到���组文件",
                    JOptionPane.YES_NO_OPTION);
            if (option == JOptionPane.YES_OPTION) {
                String docPath = System.getProperty("user.home") + File.separator + "Documents" + File.separator + "ChatFiles" + File.separator + currentUser + File.separator + groupName + "#" + subGroupId;
                File groupDir = new File(docPath);
                if (!groupDir.exists()) groupDir.mkdirs();
                JFileChooser fileChooser = new JFileChooser(groupDir);
                fileChooser.setSelectedFile(new File(groupDir, fileName));
                if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    String savePath = fileChooser.getSelectedFile().getPath();
                    FileTransferManager.downloadFile(client.getSocket(), fileId, savePath,
                            new com.chat.file.FileTransferListener() {
                                @Override
                                public void onProgress(int percentage) {
                                }
                                @Override
                                public void onComplete(String filePath) {
                                    appendMessage(sender + " 发送的文件已下��完成: " + fileName);
                                    JOptionPane.showMessageDialog(SubGroupChatWindow.this, "文件下载完成!\n保存路径: " + filePath);
                                }
                                @Override
                                public void onError(String error) {
                                    JOptionPane.showMessageDialog(SubGroupChatWindow.this,
                                            "下载失败: " + error,
                                            "错误",
                                            JOptionPane.ERROR_MESSAGE);
                                }
                            });
                }
            }
        }
    }

    // 小组管理对话框
    public void showSubGroupDialog(String groupName) {
        System.out.println("[SubGroupUI] showSubGroupDialog called, groupName=" + groupName);
        lastSGListGroupName = groupName;
        client.sendMessage("/sg_list " + groupName);
        // 若已存��dialog则先关闭
        JDialog oldDialog = groupSubGroupDialogMap.get(groupName);
        if (oldDialog != null) {
            oldDialog.dispose();
        }
        JDialog dialog = new JDialog(parentFrame, "小组管理 - " + groupName, true);
        dialog.setAlwaysOnTop(false); // 显式取消置顶
        dialog.setSize(520, 420);
        dialog.setLocationRelativeTo(parentFrame);
        JPanel panel = new JPanel(new BorderLayout());
        JPanel subGroupListPanel = new JPanel();
        subGroupListPanel.setLayout(new BoxLayout(subGroupListPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(subGroupListPanel);
        panel.add(scroll, BorderLayout.CENTER);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton createBtn = new JButton("创建小组");
        btnPanel.add(createBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        groupSubGroupPanelMap.put(groupName, subGroupListPanel);
        groupSubGroupDialogMap.put(groupName, dialog);
        // 优化：初始显示和SG_LIST消息到达后自动刷新
        updateSubGroupListPanel(groupName, subGroupListPanel, dialog);
        createBtn.addActionListener(e -> {
            String sgName = JOptionPane.showInputDialog(dialog, "小组名称:");
            String members = JOptionPane.showInputDialog(dialog, "初始成员(逗号分隔，可留空):");
            if (sgName != null && !sgName.trim().isEmpty()) {
                StringBuilder cmd = new StringBuilder("/sg_create " + groupName + " " + sgName.trim());
                if (members != null && !members.trim().isEmpty()) {
                    for (String m : members.split(",")) {
                        String user = m.trim();
                        if (!user.isEmpty()) {
                            cmd.append(" ").append(user);
                        }
                    }
                }
                cmd.append(" ").append(currentUser);
                client.sendMessage(cmd.toString());
                JOptionPane.showMessageDialog(dialog, "已发送创建小组请求���等待服务器响应。", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                groupSubGroupDialogMap.remove(groupName);
            }
        });
        dialog.setVisible(true);
    }

    // 解析SG_LIST消息并刷新UI
    public void onSubGroupListReceived(String groupName, String info) {
        System.out.println("[SubGroupUI] onSubGroupListReceived groupName=" + groupName + ", info=" + info);
        subGroupInfoCache.put(groupName, info);
        Map<String, Set<String>> sgMap = new LinkedHashMap<>();
        if (info != null && !info.trim().isEmpty()) {
            String[] groups = info.split(";");
            for (String g : groups) {
                String[] parts = g.split("\\|");
                if (parts.length >= 3) {
                    String subGroupId = parts[0].trim();
                    String[] members = parts[2].split(",");
                    Set<String> memberSet = new HashSet<>();
                    for (String m : members) {
                        if (!m.trim().isEmpty()) memberSet.add(m.trim());
                    }
                    sgMap.put(subGroupId, memberSet);
                }
            }
        }
        System.out.println("[SubGroupUI] subGroupMemberMap=" + sgMap);
        subGroupMemberMap.put(groupName, sgMap);
        // 刷新UI，拿到当前dialog和panel
        JPanel panel = groupSubGroupPanelMap.get(groupName);
        JDialog dialog = groupSubGroupDialogMap.get(groupName);
        updateSubGroupListPanel(groupName, panel, dialog);
    }

    // 动态刷新小组列表面板
    public void updateSubGroupListPanel(String groupName, JPanel subGroupListPanel, JDialog parentDialog) {
        if (subGroupListPanel == null && groupName != null) {
            subGroupListPanel = groupSubGroupPanelMap.get(groupName);
        }
        if (subGroupListPanel == null) return;
        subGroupListPanel.removeAll();
        Map<String, Set<String>> sgMap = subGroupMemberMap.get(groupName);
        if (sgMap == null || sgMap.isEmpty()) {
            JLabel empty = new JLabel("暂存小组信息 (无数据)\n原始info: " + subGroupInfoCache.getOrDefault(groupName, "(空)"));
            subGroupListPanel.add(empty);
        } else {
            java.util.List<String> mySubGroups = new ArrayList<>();
            java.util.List<String> otherSubGroups = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : sgMap.entrySet()) {
                if (entry.getValue().contains(currentUser)) {
                    mySubGroups.add(entry.getKey());
                } else {
                    otherSubGroups.add(entry.getKey());
                }
            }
            // 我的分区
            JLabel myTitle = new JLabel("我所在的小组");
            myTitle.setFont(new Font("微软雅黑", Font.BOLD, 16));
            myTitle.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 0));
            subGroupListPanel.add(myTitle);
            if (mySubGroups.isEmpty()) {
                JLabel none = new JLabel("暂无");
                none.setAlignmentX(Component.LEFT_ALIGNMENT);
                subGroupListPanel.add(none);
            } else {
                for (String sgId : mySubGroups) {
                    Set<String> members = sgMap.get(sgId);
                    String sgName = "";
                    String info = subGroupInfoCache.getOrDefault(groupName, "");
                    // 解析小组名称
                    String[] groupArr = info.split(";");
                    for (String g : groupArr) {
                        String[] parts = g.split("\\|");
                        if (parts.length >= 2 && parts[0].trim().equals(sgId)) {
                            sgName = parts[1].trim();
                            break;
                        }
                    }
                    JPanel card = new JPanel(new BorderLayout());
                    card.setMaximumSize(new Dimension(480, 44));
                    card.setPreferredSize(new Dimension(480, 44));
                    card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(120,180,255), 1, true),
                        BorderFactory.createEmptyBorder(4, 16, 4, 16)));
                    card.setBackground(new Color(235, 245, 255));
                    JLabel label = new JLabel("名称:" + sgName + "  成员:" + String.join(",", members));
                    label.setFont(new Font("微软雅黑", Font.PLAIN, 14));
                    JButton chatBtn = new JButton("进入聊天");
                    chatBtn.setFocusable(false);
                    chatBtn.addActionListener(e -> openSubGroupChatWindow(groupName, sgId));
                    JButton exitBtn = new JButton("退出小组");
                    exitBtn.setFocusable(false);
                    exitBtn.addActionListener(e -> {
                        int ok = JOptionPane.showConfirmDialog(parentDialog, "确定要退出该小组吗？", "确认", JOptionPane.YES_NO_OPTION);
                        if (ok == JOptionPane.YES_OPTION) {
                            client.sendMessage("/sg_exit " + groupName);
                        }
                    });
                    // 新增：邀请成员按钮
                    JButton inviteBtn = new JButton("邀请成员");
                    inviteBtn.setFocusable(false);
                    inviteBtn.addActionListener(e -> {
                        String invitee = JOptionPane.showInputDialog(parentDialog, "请输入要邀请的群成员用户名:");
                        if (invitee != null && !invitee.trim().isEmpty()) {
                            client.sendMessage("/sg_invite " + groupName + " " + sgId + " " + invitee.trim());
                        }
                    });
                    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                    btnPanel.setOpaque(false);
                    btnPanel.add(chatBtn);
                    btnPanel.add(inviteBtn);
                    btnPanel.add(exitBtn);
                    card.add(label, BorderLayout.CENTER);
                    card.add(btnPanel, BorderLayout.EAST);
                    subGroupListPanel.add(card);
                    subGroupListPanel.add(Box.createVerticalStrut(6));
                }
            }
            // 其他分区
            JLabel otherTitle = new JLabel("其他小组");
            otherTitle.setFont(new Font("微软雅黑", Font.BOLD, 16));
            otherTitle.setBorder(BorderFactory.createEmptyBorder(16, 10, 5, 0));
            subGroupListPanel.add(otherTitle);
            if (otherSubGroups.isEmpty()) {
                JLabel none = new JLabel("暂无");
                none.setAlignmentX(Component.LEFT_ALIGNMENT);
                subGroupListPanel.add(none);
            } else {
                for (String sgId : otherSubGroups) {
                    Set<String> members = sgMap.get(sgId);
                    String sgName = "";
                    String info = subGroupInfoCache.getOrDefault(groupName, "");
                    String[] groupArr = info.split(";");
                    for (String g : groupArr) {
                        String[] parts = g.split("\\|");
                        if (parts.length >= 2 && parts[0].trim().equals(sgId)) {
                            sgName = parts[1].trim();
                            break;
                        }
                    }
                    JPanel card = new JPanel(new BorderLayout());
                    card.setMaximumSize(new Dimension(480, 40));
                    card.setPreferredSize(new Dimension(480, 40));
                    card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220,220,220), 1, true),
                        BorderFactory.createEmptyBorder(4, 16, 4, 16)));
                    card.setBackground(new Color(255, 252, 240));
                    JLabel label = new JLabel("名称:" + sgName + "  成员:" + String.join(",", members));
                    label.setFont(new Font("微软雅黑", Font.PLAIN, 13));
                    JButton joinBtn = new JButton("加入小组");
                    joinBtn.setFocusable(false);
                    joinBtn.addActionListener(e -> {
                        int ok = JOptionPane.showConfirmDialog(parentDialog, "确定要加入该小组吗？", "确认", JOptionPane.YES_NO_OPTION);
                        if (ok == JOptionPane.YES_OPTION) {
                            client.sendMessage("/sg_join " + groupName + " " + sgId);
                        }
                    });
                    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                    btnPanel.setOpaque(false);
                    btnPanel.add(joinBtn);
                    card.add(label, BorderLayout.CENTER);
                    card.add(btnPanel, BorderLayout.EAST);
                    subGroupListPanel.add(card);
                    subGroupListPanel.add(Box.createVerticalStrut(6));
                }
            }
        }
        subGroupListPanel.revalidate();
        subGroupListPanel.repaint();
    }

    // 新增：打开“我的图片”文件夹（子群专用��

}
