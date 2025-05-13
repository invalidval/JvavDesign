package com.chat.ui;

import com.chat.client.Client;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

public class LoginWindow extends JFrame {
    private JTextField usernameField;
    private JButton loginButton;

    public LoginWindow() {
        setTitle("登录");
        setLayout(null);

        JLabel usernameLabel = new JLabel("用户名:");
        usernameLabel.setBounds(50, 50, 80, 30);
        add(usernameLabel);

        usernameField = new JTextField();
        usernameField.setBounds(130, 50, 150, 30);
        add(usernameField);

        loginButton = new JButton("登录");
        loginButton.setBounds(130, 100, 80, 30);
        add(loginButton);

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = usernameField.getText();
                if (!username.isEmpty()) {
                    try {
                        Client client = new Client("localhost", 8888);
                        client.sendMessage("LOGIN:" + username); // 向服务器发送登录消息
                        new ChatWindow(username, client).setVisible(true);
                        dispose();
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(null, "无法连接到服务器！");
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "用户名不能为空！");
                }
            }
        });

        JButton addFriendButton = new JButton("添加好友");
        addFriendButton.setBounds(220, 100, 100, 30);
        add(addFriendButton);

        addFriendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String friendName = JOptionPane.showInputDialog("请输入好友用户名:");
                if (friendName != null && !friendName.isEmpty()) {
                    // TODO: 发送添加好友请求到服务器
                    JOptionPane.showMessageDialog(null, "好友请求已发送给 " + friendName);
                }
            }
        });

        setSize(400, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginWindow loginWindow = new LoginWindow();
            loginWindow.setVisible(true);
        });
    }
}
