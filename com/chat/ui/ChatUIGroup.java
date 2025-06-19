package com.chat.ui;

import com.chat.client.Client;
import com.chat.client.MessageObserver;

import javax.swing.*;
import java.awt.*;
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
        client.addObserver(this);
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
                GroupChatWindow win = groupChats.computeIfAbsent(group, gn -> {
                    GroupChatWindow w = new GroupChatWindow(gn);
                    w.setVisible(true);
                    return w;
                });
                win.appendMessage(sender + ": " + msg);
                win.setVisible(true);
            }
        } else if (message.startsWith("SUCCESS: 创建群聊成功")) {
            JOptionPane.showMessageDialog(parentFrame, message);
            requestGroupList();
        } else if (message.startsWith("SUCCESS: 加入群聊成功")) { // 新增处理加入群聊成功的消息
            JOptionPane.showMessageDialog(parentFrame, message);
            requestGroupList(); // 刷新群聊列表
        } else if (message.startsWith("ERROR: 加入群聊失败")) { // 新增处理加入群聊失败的消息
            JOptionPane.showMessageDialog(parentFrame, message, "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void requestGroupList() {
        client.sendMessage("/glist");
    }

    // 群聊窗口内部类
    class GroupChatWindow extends JFrame {
        private JTextArea chatArea;
        private JTextField inputField;
        private String groupName;

        public GroupChatWindow(String groupName) {
            super("群聊 - " + groupName);
            this.groupName = groupName;
            setSize(400, 300);
            setLayout(new BorderLayout());

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

            add(scrollPane, BorderLayout.CENTER);
            add(inputPanel, BorderLayout.SOUTH);

            // 可扩展：显示群成员列表
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
            chatArea.append(msg + "\n");
        }
    }
}