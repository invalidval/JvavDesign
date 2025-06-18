# 项目修改日志 (hhb.md)

## 修改时间

2025-06-18

---

## 功能1：用户漫游功能

### 实现内容

- 同名用户只能在一台设备登录
- 新设备登录时自动踢掉旧设备
- 友好的踢下线提示消息

### 文件修改

**1. com/chat/server/UserDatabase.java**

- 第81-101行：修改loginUser()方法，添加漫游功能踢用户逻辑
- 第108-124行：新增kickExistingUser()和notifyServerToKickUser()方法
- 第219-222行：新增ServerUserObserver接口，扩展onUserKicked()方法

**2. com/chat/server/Server.java**

- 第11行：添加User类导入
- 第13行：实现UserDatabase.ServerUserObserver接口
- 第17行：添加serverInstance静态变量
- 第34行：初始化消息存储系统
- 第36行：注册服务器为观察者
- 第58-61行：修改removeClient()方法，添加用户状态同步
- 第68-76行：新增kickUser()方法
- 第112-123行：实现onUserChanged()和onUserKicked()接口方法

**3. com/chat/server/ClientHandler.java**

- 第130-143行：新增forceDisconnect()方法

**4. com/chat/client/Client.java**

- 无修改（保持原有架构兼容性）

### 测试结果

✅ 功能正常工作，同名用户互斥登录成功实现

---

## 功能2：消息记录功能

### 实现内容

- 自动保存所有群聊和私聊消息
- 重新登录时自动显示历史消息
- 支持多种历史消息查询命令
- 基于XML文件持久化存储

### 文件修改

**1. com/chat/server/MessageStorage.java (新增文件)**

- 完整的消息存储管理类
- saveMessage()方法：保存消息到XML
- getUserMessages()和getPrivateMessages()方法：查询历史消息
- XML文件操作和内存缓存机制

**2. com/chat/server/ClientHandler.java**

- 第12行：添加MessageStorage导入
- 第35行：注册HistoryMessageHandler("/H"命令)
- 第77行：登录成功后调用showRecentMessagesOnLogin()
- 第148-158行：新增showRecentMessagesOnLogin()方法
- 第213-217行：修改PrivateMessageHandler，添加消息保存
- 第312-389行：新增HistoryMessageHandler类，支持多种查询格式
- 第393-399行：修改DefaultMessageHandler，添加群聊消息保存

**3. com/chat/server/Server.java**

- 第10行：添加MessageStorage导入
- 第34行：初始化消息存储系统

### 查询命令

- `/h` - 查看最近20条消息
- `/h 数量` - 查看指定数量消息
- `/h 用户名` - 查看与指定用户的私聊记录
- `/h 用户名 数量` - 查看指定数量的私聊记录

### 测试结果

✅ 功能正常工作，消息自动保存和查询功能完整实现
