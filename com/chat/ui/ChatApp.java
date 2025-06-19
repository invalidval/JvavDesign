package com.chat.ui;

import com.chat.client.Client;
import com.chat.client.MessageObserver;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChatApp implements MessageObserver, ChatWindowLimitProvider {
    private JFrame frame ;
    private JPanel loginPanel;
    private Client client;
    private String currentUser;

    // 主界面相关
    private JPanel mainPanel;
    private CardLayout mainCardLayout;
    private MessageListPanel messageListPanel;
    private ChatUIFriends friendsUI;
    private ChatUIGroup groupUI;
    private ChatUIHistory historyUI; // 聊天记录界面

    // 消息列表数据：key=会话名（好友或群名），value=最近一条消息
    private Map<String, String> sessionLastMsg = new ConcurrentHashMap<>();
    // 会话类型：true=群聊，false=私聊
    private Map<String, Boolean> sessionIsGroup = new ConcurrentHashMap<>();

    // 聊天窗口最大数量设置
    private int maxChatWindows = 5;

    // 背景面板内部类
    class BackgroundPanel extends JPanel {
        private Image bgImage;
        public BackgroundPanel(String imagePath) {
            try {
                bgImage = new ImageIcon(getClass().getClassLoader().getResource(imagePath)).getImage();
            } catch (Exception e) {
                bgImage = null;
            }
            setLayout(new GridBagLayout());
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (bgImage != null) {
                // 自动缩放填充整个面板
                g.drawImage(bgImage, 0, 0, getWidth(), getHeight(), this);
            }
        }
    }

    public ChatApp() {
        frame = new JFrame("Chat Application");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 500);
        frame.setLayout(new CardLayout());

        createLoginPanel();

        frame.add(loginPanel, "Login");
        frame.setLocationRelativeTo(null); // 让窗口居中显示
        frame.setVisible(true);
    }

    private void createLoginPanel() {
        // loginPanel = new JPanel(new GridBagLayout());
        loginPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel usernameLabel = new JLabel("用户名：");
        JTextField usernameField = new JTextField();
        usernameField.setPreferredSize(new Dimension(200, 30));

        JLabel passwordLabel = new JLabel("密码：");
        JPasswordField passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(200, 30));

        JButton loginButton = new JButton("登录");
        loginButton.setPreferredSize(new Dimension(120, 35));
        JButton registerButton = new JButton("注册");
        registerButton.setPreferredSize(new Dimension(120, 35));

        gbc.gridx = 0;
        gbc.gridy = 0;
        loginPanel.add(usernameLabel, gbc);
        gbc.gridx = 1;
        loginPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        loginPanel.add(passwordLabel, gbc);
        gbc.gridx = 1;
        loginPanel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        loginPanel.add(loginButton, gbc);
        gbc.gridx = 1;
        loginPanel.add(registerButton, gbc);

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

        // 设置背景面板，使用lg_bg.png
        BackgroundPanel bgPanel = new BackgroundPanel("resources/lg_bg.jpg");
        bgPanel.add(loginPanel); // 将原有loginPanel加到背景面板上
        this.loginPanel = bgPanel; // 用背景面板替换原有loginPanel
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
        } else if (message.startsWith("SYSTEM:")) {
            int statusCodeStart = message.lastIndexOf("[");
            int statusCodeEnd = message.lastIndexOf("]");
            if (statusCodeStart != -1 && statusCodeEnd != -1) {
                String statusCode = message.substring(statusCodeStart + 1, statusCodeEnd).trim();
                if ("000".equals(statusCode)) {
                    JOptionPane.showMessageDialog(frame, "检测到异地登录，本客户端将下线！", "警告", JOptionPane.WARNING_MESSAGE);
                    CardLayout cl = (CardLayout) frame.getContentPane().getLayout();
                    cl.show(frame.getContentPane(), "Login");

                    // 停止客户端监听并重新初始化
                    client.stopListening();
                    try {
                        client = new Client("localhost", 8888); // 重新初始化客户端
                        client.addObserver(this);
                        client.startListening();
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(frame, "无法重新连接到服务器：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }else if(message.startsWith("ONLINE:")|| message.startsWith("STATUS:")){
            if (friendsUI != null) friendsUI.onMessageReceived(message);

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
            friendsUI = new ChatUIFriends(client, frame, currentUser, this); // 传递 ChatWindowLimitProvider 实例
            mainPanel.add(friendsUI.getPanel(), "Friends");

            // 群聊列表面板
            groupUI = new ChatUIGroup(client, frame, currentUser, this); // 传递 ChatWindowLimitProvider 实例
            mainPanel.add(groupUI.getPanel(), "Groups");

            // 聊天记录面板
            historyUI = new ChatUIHistory(client, frame, currentUser);
            mainPanel.add(historyUI.getPanel(), "History");

            // 顶部导航栏
            JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton msgBtn = new JButton("消息");
            JButton friendsBtn = new JButton("好友");
            JButton groupsBtn = new JButton("群聊");
            JButton historyBtn = new JButton("聊天记录");
            JButton settingsBtn = new JButton("设置"); // 新增设置按钮
            navBar.add(msgBtn);
            navBar.add(friendsBtn);
            navBar.add(groupsBtn);
            navBar.add(historyBtn);
            navBar.add(settingsBtn); // 添加到导航栏

            msgBtn.addActionListener(e -> mainCardLayout.show(mainPanel, "Messages"));
            friendsBtn.addActionListener(e -> mainCardLayout.show(mainPanel, "Friends"));
            groupsBtn.addActionListener(e -> mainCardLayout.show(mainPanel, "Groups"));
            historyBtn.addActionListener(e -> mainCardLayout.show(mainPanel, "History"));

            // 设置按钮弹出设置对话框
            settingsBtn.addActionListener(e -> showSettingsDialog());

            JPanel container = new JPanel(new BorderLayout());
            container.add(navBar, BorderLayout.NORTH);
            container.add(mainPanel, BorderLayout.CENTER);

            frame.add(container, "Main");
        }
        CardLayout cl = (CardLayout) frame.getContentPane().getLayout();
        cl.show(frame.getContentPane(), "Main");
        mainCardLayout.show(mainPanel, "Messages");
    }

    // 设置对话框
    private void showSettingsDialog() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel("最大聊天窗口数：");
        SpinnerNumberModel model = new SpinnerNumberModel(maxChatWindows, 1, 20, 1);
        JSpinner spinner = new JSpinner(model);
        panel.add(label);
        panel.add(spinner);

        int result = JOptionPane.showConfirmDialog(frame, panel, "设置", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            maxChatWindows = (Integer) spinner.getValue();
        }
    }

    public void start() {
        try {
            client = new Client("localhost", 8888);
            client.addObserver(this); // 确保只注册一次
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
            // 设置listPanel透明，避免背景色影响
            listPanel.setOpaque(false);

            JScrollPane scrollPane = new JScrollPane(listPanel);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            add(scrollPane, BorderLayout.CENTER);
            refresh();
        }

        public void refresh() {
            listPanel.removeAll();
            if (sessionLastMsg.isEmpty()) {
                JLabel emptyLabel = new JLabel("暂无消息");
                emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                listPanel.add(emptyLabel);
            } else {
                // 按最近更新时间排序
                List<Map.Entry<String, String>> entries = new ArrayList<>(sessionLastMsg.entrySet());
                for (Map.Entry<String, String> entry : entries) {
                    String session = entry.getKey();
                    String lastMsg = entry.getValue();
                    boolean isGroup = sessionIsGroup.getOrDefault(session, false);
                    JPanel card = new JPanel(new BorderLayout());
                    card.setMaximumSize(new Dimension(600, 50));
                    card.setPreferredSize(new Dimension(600, 50));
                    card.setMinimumSize(new Dimension(600, 50));
                    card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
                    card.setAlignmentX(Component.CENTER_ALIGNMENT);

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
                    listPanel.add(Box.createVerticalStrut(8));
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

    @Override
    public int getMaxChatWindows() {
        return maxChatWindows;
    }

    @Override
    public int getCurrentOpenChatWindowCount() {
        // 返回当前已打开的群聊窗口数和私聊窗口数之和
        return (friendsUI != null ? friendsUI.getOpenChatWindowCount() : 0) +
               (groupUI != null ? groupUI.getOpenChatWindowCount() : 0);
    }
}

// 聊天窗口数限制接口
interface ChatWindowLimitProvider {
    int getMaxChatWindows();
    int getCurrentOpenChatWindowCount();
}