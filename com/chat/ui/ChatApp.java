package com.chat.ui;

import com.chat.client.Client;
import com.chat.client.MessageObserver;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChatApp implements MessageObserver {
    private JFrame frame;
    private JPanel loginPanel;
    private Client client;
    private String currentUser;

    // 主界面相关
    private JPanel mainPanel;
    private CardLayout mainCardLayout;
    private MessageListPanel messageListPanel;
    private ChatUIFriends friendsUI;
    private ChatUIGroup groupUI;

    // 消息列表数据：key=会话名（好友或群名），value=最近一条消息
    private Map<String, String> sessionLastMsg = new ConcurrentHashMap<>();
    // 会话类型：true=群聊，false=私聊
    private Map<String, Boolean> sessionIsGroup = new ConcurrentHashMap<>();

    public ChatApp() {
        frame = new JFrame("Chat Application");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 500);
        frame.setLayout(new CardLayout());

        createLoginPanel();

        frame.add(loginPanel, "Login");
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

        loginButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            if (!username.isEmpty() && !password.isEmpty()) {
                client.sendMessage("/l " + username + " " + password);
                currentUser = username;
            } else {
                JOptionPane.showMessageDialog(frame, "用户名和密码不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        registerButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            if (!username.isEmpty() && !password.isEmpty()) {
                client.sendMessage("/r " + username + " " + password);
            } else {
                JOptionPane.showMessageDialog(frame, "用户名和密码不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    // 消息观察者回调
    @Override
    public void onMessageReceived(String message) {
        if (message == null) return;
        if (message.startsWith("SUCCESS: 登录成功")) {
            JOptionPane.showMessageDialog(frame, "登录成功！");
            showMainUI();
            client.sendMessage("/f"); // 登录后自动请求好友列表
            client.sendMessage("/glist"); // 登录后自动请求群聊列表
        } else if (message.startsWith("SUCCESS: 注册成功")) {
            JOptionPane.showMessageDialog(frame, message);
        } else if (message.startsWith("ERROR:")) {
            JOptionPane.showMessageDialog(frame, message, "错误", JOptionPane.ERROR_MESSAGE);
        } else if (message.startsWith("FRIENDS:") || message.startsWith("SUCCESS: 好友添加成功") || message.startsWith("SUCCESS: 好友删除成功") || message.startsWith("[私聊]")) {
            if (friendsUI != null) friendsUI.onMessageReceived(message);
            // 私聊消息也加入消息列表
            if (message.startsWith("[私聊]")) {
                int idx1 = message.indexOf("] ");
                int idx2 = message.indexOf(":", idx1 + 2);
                if (idx1 != -1 && idx2 != -1) {
                    String sender = message.substring(idx1 + 2, idx2).trim();
                    String msg = message.substring(idx2 + 1).trim();
                    if (!sender.equals(currentUser)) {
                        sessionLastMsg.put(sender, sender + ": " + msg);
                        sessionIsGroup.put(sender, false);
                        if (messageListPanel != null) messageListPanel.refresh();
                    }
                }
            }
        } else if (message.startsWith("GROUPS:") || message.startsWith("SUCCESS: 创建群聊成功") || message.startsWith("[群聊]")) {
            if (groupUI != null) groupUI.onMessageReceived(message);
            // 群聊消息也加入消息列表
            if (message.startsWith("[群聊]")) {
                int idx1 = message.indexOf("] ");
                int idx2 = message.indexOf("|", idx1 + 2);
                int idx3 = message.indexOf(":", idx2 + 1);
                if (idx1 != -1 && idx2 != -1 && idx3 != -1) {
                    String group = message.substring(idx1 + 2, idx2).trim();
                    String sender = message.substring(idx2 + 1, idx3).trim();
                    String msg = message.substring(idx3 + 1).trim();
                    sessionLastMsg.put(group, sender + ": " + msg);
                    sessionIsGroup.put(group, true);
                    if (messageListPanel != null) messageListPanel.refresh();
                }
            }
        } else {
            JOptionPane.showMessageDialog(frame, message);
        }
    }

    private void showMainUI() {
        if (mainPanel == null) {
            mainCardLayout = new CardLayout();
            mainPanel = new JPanel(mainCardLayout);

            // 消息列表面板
            messageListPanel = new MessageListPanel();
            mainPanel.add(messageListPanel, "Messages");

            // 好友列表面板
            friendsUI = new ChatUIFriends(client, frame, currentUser);
            mainPanel.add(friendsUI.getPanel(), "Friends");

            // 群聊列表面板
            groupUI = new ChatUIGroup(client, frame, currentUser);
            mainPanel.add(groupUI.getPanel(), "Groups");

            // 顶部导航栏
            JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton msgBtn = new JButton("消息");
            JButton friendsBtn = new JButton("好友");
            JButton groupsBtn = new JButton("群聊");
            navBar.add(msgBtn);
            navBar.add(friendsBtn);
            navBar.add(groupsBtn);

            msgBtn.addActionListener(e -> mainCardLayout.show(mainPanel, "Messages"));
            friendsBtn.addActionListener(e -> mainCardLayout.show(mainPanel, "Friends"));
            groupsBtn.addActionListener(e -> mainCardLayout.show(mainPanel, "Groups"));

            JPanel container = new JPanel(new BorderLayout());
            container.add(navBar, BorderLayout.NORTH);
            container.add(mainPanel, BorderLayout.CENTER);

            frame.add(container, "Main");
        }
        CardLayout cl = (CardLayout) frame.getContentPane().getLayout();
        cl.show(frame.getContentPane(), "Main");
        mainCardLayout.show(mainPanel, "Messages");
    }

    public void start() {
        try {
            client = new Client("localhost", 8888);
            client.addObserver(this);
            client.startListening();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "无法连接到服务器：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // 消息列表面板
    class MessageListPanel extends JPanel {
        private JPanel listPanel;

        public MessageListPanel() {
            setLayout(new BorderLayout());
            listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            JScrollPane scrollPane = new JScrollPane(listPanel);
            add(scrollPane, BorderLayout.CENTER);
            refresh();
        }

        public void refresh() {
            listPanel.removeAll();
            if (sessionLastMsg.isEmpty()) {
                listPanel.add(new JLabel("暂无消息"));
            } else {
                // 按最近更新时间排序
                List<Map.Entry<String, String>> entries = new ArrayList<>(sessionLastMsg.entrySet());
                for (Map.Entry<String, String> entry : entries) {
                    String session = entry.getKey();
                    String lastMsg = entry.getValue();
                    boolean isGroup = sessionIsGroup.getOrDefault(session, false);
                    JPanel card = new JPanel(new BorderLayout());
                    card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
                    JLabel nameLabel = new JLabel(isGroup ? "[群] " + session : session);
                    JLabel msgLabel = new JLabel(lastMsg);
                    JButton openBtn = new JButton("打开");
                    openBtn.addActionListener(e -> {
                        if (isGroup) {
                            groupUI.openGroupChatWindow(session);
                        } else {
                            friendsUI.openPrivateChatWindow(session);
                        }
                    });
                    card.add(nameLabel, BorderLayout.WEST);
                    card.add(msgLabel, BorderLayout.CENTER);
                    card.add(openBtn, BorderLayout.EAST);
                    listPanel.add(card);
                }
            }
            listPanel.revalidate();
            listPanel.repaint();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatApp app = new ChatApp();
            app.start();
        });
    }
}
