package com.chat.ui;

import com.chat.client.Client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;

public class ChatApp {
    private JFrame frame;
    private JPanel loginPanel;
    private JPanel friendsPanel;
    private Client client;

    public ChatApp() {
        frame = new JFrame("Chat Application");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new CardLayout());

        createLoginPanel();
        createFriendsPanel();

        frame.add(loginPanel, "Login");
        frame.add(friendsPanel, "Friends");

        frame.setVisible(true);
    }

    private void createLoginPanel() {
        loginPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        JLabel usernameLabel = new JLabel("用户名：");
        JTextField usernameField = new JTextField();
        JLabel passwordLabel = new JLabel("密码：");
        JPasswordField passwordField = new JPasswordField();
        JButton loginButton = new JButton("登录");
        JButton registerButton = new JButton("注册");

        loginPanel.add(usernameLabel);
        loginPanel.add(usernameField);
        loginPanel.add(passwordLabel);
        loginPanel.add(passwordField);
        loginPanel.add(loginButton);
        loginPanel.add(registerButton);

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword()).trim();
                if (!username.isEmpty() && !password.isEmpty()) {
                    try {
                        client.sendMessage("/l " + username + " " + password);
                        String response = client.receiveMessage();
                        if (response.startsWith("SUCCESS: 登录成功")) {
                            JOptionPane.showMessageDialog(frame, "登录成功！");
                            showFriendsPanel();
                        } else {
                            JOptionPane.showMessageDialog(frame, response, "登录失败", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(frame, "无法连接到服务器：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(frame, "用户名和密码不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        registerButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText().trim();
                String password = new String(passwordField.getPassword()).trim();
                if (!username.isEmpty() && !password.isEmpty()) {
                    try {
                        client.sendMessage("/r " + username + " " + password);
                        String response = client.receiveMessage();
                        JOptionPane.showMessageDialog(frame, response);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(frame, "无法连接到服务器：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(frame, "用户名和密码不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void createFriendsPanel() {
        friendsPanel = new JPanel();
        friendsPanel.setLayout(new BorderLayout());

        JPanel friendsListPanel = new JPanel();
        friendsListPanel.setLayout(new BoxLayout(friendsListPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(friendsListPanel);

        JButton addFriendButton = new JButton("添加好友");
        addFriendButton.addActionListener(e -> {
            String friendName = JOptionPane.showInputDialog(frame, "请输入好友用户名：");
            if (friendName != null && !friendName.trim().isEmpty()) {
                client.sendMessage("/a " + friendName.trim());
                try {
                    String response = client.receiveMessage();
                    JOptionPane.showMessageDialog(frame, response);
                    showFriendsPanel(); // 刷新好友列表
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "无法添加好友：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        friendsPanel.add(scrollPane, BorderLayout.CENTER);
        friendsPanel.add(addFriendButton, BorderLayout.SOUTH);
    }

    private void showFriendsPanel() {
        try {
            client.sendMessage("/f");
            String response = client.receiveMessage();
            JPanel friendsListPanel = (JPanel) ((JScrollPane) friendsPanel.getComponent(0)).getViewport().getView();
            friendsListPanel.removeAll();

            if (response != null && response.startsWith("FRIENDS:")) { // 仅处理 FRIENDS: 开头的消息
                String[] friends = response.substring(8).split(",");
                for (String friend : friends) {
                    JPanel friendCard = new JPanel(new BorderLayout());
                    friendCard.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                    JLabel friendLabel = new JLabel(friend.trim());
                    JButton chatButton = new JButton("私聊");
                    chatButton.addActionListener(e -> openPrivateChatWindow(friend.trim()));
                    friendCard.add(friendLabel, BorderLayout.CENTER);
                    friendCard.add(chatButton, BorderLayout.EAST);
                    friendsListPanel.add(friendCard);
                }
            } else {
                JLabel noFriendsLabel = new JLabel("暂无好友");
                friendsListPanel.add(noFriendsLabel);
            }

            friendsListPanel.revalidate();
            friendsListPanel.repaint();
            CardLayout cl = (CardLayout) frame.getContentPane().getLayout();
            cl.show(frame.getContentPane(), "Friends");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "无法加载好友列表：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openPrivateChatWindow(String friendName) {
        JFrame privateChatFrame = new JFrame("与 " + friendName + " 私聊");
        privateChatFrame.setSize(400, 300);
        privateChatFrame.setLayout(new BorderLayout());

        JTextArea chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JTextField inputField = new JTextField();
        JButton sendButton = new JButton("发送");

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        sendButton.addActionListener(e -> {
            String message = inputField.getText().trim();
            if (!message.isEmpty()) {
                client.sendMessage("/p " + friendName + " " + message);
                chatArea.append("我: " + message + "\n");
                inputField.setText("");
            }
        });

        privateChatFrame.add(scrollPane, BorderLayout.CENTER);
        privateChatFrame.add(inputPanel, BorderLayout.SOUTH);
        privateChatFrame.setVisible(true);
    }

    public void start() {
        try {
            client = new Client("localhost", 8888);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "无法连接到服务器：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatApp app = new ChatApp();
            app.start();
        });
    }
}
