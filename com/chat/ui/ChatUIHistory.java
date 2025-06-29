package com.chat.ui;

import com.chat.client.Client;
import com.chat.client.MessageObserver;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ChatUIHistory implements MessageObserver {
    private JPanel historyPanel;
    private JTextArea historyArea;
    private JTextField queryField;
    private Client client;
    private String currentUser;

    public ChatUIHistory(Client client, JFrame parentFrame, String currentUser) {
        this.client = client;
        this.currentUser = currentUser;
        client.addObserver(this);
        createHistoryPanel();
    }

    // 新增：支持作为弹窗，自动填充查询参数
    public ChatUIHistory(Client client, JFrame parentFrame, String currentUser, String type, String targetName) {
        this.client = client;
        this.currentUser = currentUser;
        client.addObserver(this);
        createHistoryPanel(type, targetName);
    }

    public JPanel getPanel() {
        return historyPanel;
    }

    private void createHistoryPanel() {
        historyPanel = new JPanel(new BorderLayout());

        historyArea = new JTextArea();
        historyArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(historyArea);

        JPanel queryPanel = new JPanel(new BorderLayout());
        queryField = new JTextField();
        JButton queryButton = new JButton("查询");

        queryButton.addActionListener(e -> {
            String query = queryField.getText().trim();
            if (query.isEmpty()) {
                client.sendMessage("/h"); // 查询最近消息
            } else if (query.startsWith("/h") || query.startsWith("/H")) {
                client.sendMessage(query);
            } else if (query.startsWith("/hg") || query.startsWith("/HG")) {
                client.sendMessage(query); // 直接发送群聊历史指令
            }
        });

        queryPanel.add(queryField, BorderLayout.CENTER);
        queryPanel.add(queryButton, BorderLayout.EAST);

        // 添加指令提示区域
        JPanel instructionPanel = new JPanel(new GridLayout(8, 1));
        instructionPanel.setBorder(BorderFactory.createTitledBorder("指令提示"));
        instructionPanel.add(new JLabel("/h                   - 查看最近20条消息"));
        instructionPanel.add(new JLabel("/h 数量               - 查看指定数量的消息"));
        instructionPanel.add(new JLabel("/h 用户名              - 查看与指定用户的私聊记录"));
        instructionPanel.add(new JLabel("/h 用户名 数量          - 查看与指定用户的指定数量私聊记录"));
        instructionPanel.add(new JLabel("/hg 群名               - 查看该群最近20条消息"));
        instructionPanel.add(new JLabel("/hg 群名 数量           - 查看该群指定数量的消息"));
        instructionPanel.add(new JLabel("/hg 群名 成员名         - 查看该群指定成员的最近20条消息"));
        instructionPanel.add(new JLabel("/hg 群名 成员名 数量    - 查看该群指定成员的指定数量消息"));

        historyPanel.add(scrollPane, BorderLayout.CENTER);
        historyPanel.add(queryPanel, BorderLayout.NORTH);
        historyPanel.add(instructionPanel, BorderLayout.SOUTH);
    }

    // 重载，支持参数
    private void createHistoryPanel(String type, String targetName) {
        historyPanel = new JPanel(new BorderLayout());
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(historyArea);

        JPanel queryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel targetLabel = new JLabel(type.equals("private") ? "好友:" : "群聊:");
        JTextField targetField = new JTextField(targetName, 10);
        targetField.setEditable(false);
        JLabel countLabel = new JLabel("条数:");
        JComboBox<String> countBox = new JComboBox<>(new String[]{"10", "20", "50", "100"});
        JButton queryButton = new JButton("查询");

        queryButton.addActionListener(e -> {
            String count = (String) countBox.getSelectedItem();
            String cmd;
            if (type.equals("private")) {
                cmd = "/h " + targetName + " " + count;
            } else {
                cmd = "/hg " + targetName + " " + count;
            }
            client.sendMessage(cmd);
        });

        queryPanel.add(targetLabel);
        queryPanel.add(targetField);
        queryPanel.add(countLabel);
        queryPanel.add(countBox);
        queryPanel.add(queryButton);

        historyPanel.add(scrollPane, BorderLayout.CENTER);
        historyPanel.add(queryPanel, BorderLayout.NORTH);
    }

    @Override
    public void onMessageReceived(String message) {
        if (message == null) return;

        if(message.startsWith("===")){
            historyArea.setText(""); // 清空历史记录区域
        }

        historyArea.append(message + "\n");
    }
}