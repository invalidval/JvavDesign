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

    public ChatUIGroup(Client client, JFrame parentFrame, String currentUser) {
        this.client = client;
        this.parentFrame = parentFrame;
        this.currentUser = currentUser;
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

        groupPanel.add(scrollPane, BorderLayout.CENTER);
        groupPanel.add(createGroupButton, BorderLayout.SOUTH);
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

    private void showGroupList() {
        groupListPanel.removeAll();
        if (myGroups.isEmpty()) {
            groupListPanel.add(new JLabel("暂无群聊"));
        } else {
            for (String group : myGroups) {
                JPanel groupCard = new JPanel(new BorderLayout());
                groupCard.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                JLabel groupLabel = new JLabel(group);
                JButton chatButton = new JButton("进入群聊");
                chatButton.addActionListener(e -> openGroupChatWindow(group));
                groupCard.add(groupLabel, BorderLayout.CENTER);
                groupCard.add(chatButton, BorderLayout.EAST);
                groupListPanel.add(groupCard);
            }
        }
        groupListPanel.revalidate();
        groupListPanel.repaint();
    }

    public void openGroupChatWindow(String groupName) {
        GroupChatWindow win = groupChats.get(groupName);
        if (win == null || !win.isDisplayable()) {
            win = new GroupChatWindow(groupName);
            groupChats.put(groupName, win);
        }
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