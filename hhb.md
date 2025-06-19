# 项目修改日志 (hhb.md)

# 2025-06-18

---

## 用户漫游功能

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

功能正常工作，同名用户互斥登录成功实现

---

## 消息记录功能

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

功能正常工作，消息自动保存和查询功能完整实现

---

# 2025-06-19

## debug：群聊消息历史记录修复

### 实现内容

- 修复群聊消息(/gs命令)不保存到历史记录的问题
- 改进群聊消息缓存机制，确保所有群成员都能查看群聊历史
- 优化群聊消息在历史记录中的显示格式

### 文件修改

**1. com/chat/server/ClientHandler.java**

- 第293行：在GroupSendHandler中添加群聊消息保存逻辑
- 新增MessageStorage.saveMessage()调用，保存群聊消息到存储系统

**2. com/chat/server/MessageStorage.java**

- 第128-157行：改进addToCache()方法，支持群聊消息缓存
- 使用反射调用GroupDatabase.getGroupMembers()获取群成员列表
- 确保群聊消息添加到所有群成员的消息缓存中
- 第343-353行：优化StoredMessage.toString()方法，群聊消息显示格式为"[时间] [群聊:群名] 发送者: 消息内容"

**3. test_group_history.java (新增文件)**

- 自动化测试脚本，验证群聊消息历史记录功能

### 修复效果

- `/gs 群名 消息内容` 发送的群聊消息现在会自动保存
- 所有群成员都可以通过 `/h` 命令查看群聊历史记录
- 群聊消息在历史记录中显示清晰的群名标识
- 保持与私聊消息历史记录的一致性

### 测试结果

群聊消息历史记录功能修复完成，所有功能正常工作

---

## debug：用户漫游功能修复

### 实现内容

- 修复第三个客户端无法踢掉第二个客户端的问题
- 解决"幽灵连接"问题（只能发消息但收不到消息）
- 重构连接管理逻辑，简化踢用户流程

### 文件修改

**1. com/chat/server/Server.java**

- 第54-64行：重构addClient()方法，在添加新连接前先踢掉同名旧连接
- 删除第74-84行：移除kickUser()方法（逻辑合并到addClient中）
- 第13行：修改接口实现，从ServerUserObserver改为UserDatabaseObserver
- 删除第112-116行：移除onUserKicked()方法

**2. com/chat/server/UserDatabase.java**

- 第91-92行：简化loginUser()方法，移除复杂的踢用户逻辑
- 删除第99-119行：移除kickExistingUser()和notifyServerToKickUser()方法
- 删除第192-194行：移除ServerUserObserver接口定义

**3. com/chat/server/ClientHandler.java**

- 第69-80行：调整登录成功后的处理顺序，确保连接管理正确

**4. test_roaming_fix.java (新增文件)**

- 自动化测试脚本，验证三个客户端依次登录的漫游功能

### 修复效果

- 第一次登录：用户正常登录
- 第二次登录：正确踢掉第一个客户端
- 第三次登录：正确踢掉第二个客户端（修复重点）
- 消息收发：当前在线设备功能完全正常
- 无幽灵连接：不再出现只能发送不能接收的连接

### 测试结果

用户漫游功能修复完成，多设备登录互斥功能正常工作
