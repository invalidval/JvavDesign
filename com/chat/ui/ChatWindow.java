package com.chat.ui;

import com.chat.client.Client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ChatWindow extends JFrame {
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JTextField privateField; // 私聊目标输入框
    private Client client;

    public ChatWindow(String username, Client client) {
        super("聊天窗口 - " + username);
        this.client = client;

        setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel privatePanel = new JPanel(new BorderLayout());
        privateField = new JTextField();
        privateField.setToolTipText("输入私聊用户名");
        privatePanel.add(new JLabel("私聊目标:"), BorderLayout.WEST);
        privatePanel.add(privateField, BorderLayout.CENTER);
        add(privatePanel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("发送");
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = inputField.getText();
                String targetUser = privateField.getText();
                if (!message.isEmpty()) {
                    if (!targetUser.isEmpty()) {
                        client.sendMessage("PRIVATE:" + targetUser + ":" + message);
                        chatArea.append("[私聊] 我 -> " + targetUser + ": " + message + "\n");
                    } else {
                        client.sendMessage(message);
                        chatArea.append("我: " + message + "\n");
                    }
                    inputField.setText("");
                }
            }
        });

        setSize(400, 300);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public void displayMessage(String message) {
        chatArea.append(message + "\n");
    }
}
