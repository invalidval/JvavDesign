# 项目修改日志 (hhb.md)

# 2025-06-20

---

## 功能1：异地登录处理完善

### 问题分析

原有的异地登录功能存在问题：

- UserDatabase.loginUser()方法缺少在线状态检测
- 没有正确返回LOGIN_ALREADY_ONLINE状态码
- 异地登录时无法正确处理踢下线逻辑

### 实现内容

- 完善异地登录检测逻辑
- 修复踢下线处理流程
- 支持多次连续异地登录
- 提供友好的登录提示信息

### 文件修改

**1. com/chat/server/UserDatabase.java**

- 第81-106行：修改loginUser()方法，添加在线状态检测
- 第121行：将notifyFriendsStatusChange()方法改为public
- 第169行：将saveUsersToFile()方法改为public

**2. com/chat/server/ClientHandler.java**

- 第89-110行：完善LOGIN_ALREADY_ONLINE状态处理逻辑
- 添加异地登录成功提示信息


---

## 功能2：修复messages.xml空行问题

### 问题分析

messages.xml文件在保存历史聊天记录时会产生大量空行，而群聊XML文件格式正常：

**问题原因**：

- saveToFile()方法每次都在现有XML文档基础上添加新消息
- XML Transformer的INDENT属性会在现有缩进基础上再次添加缩进
- 重复的格式化处理导致空行累积效应
- 每次保存都会保留之前的格式化空行，并添加新的空行

**群聊XML正常的原因**：

- saveGroupMessageToFile()方法每次重新创建整个XML文档
- 从头构建所有消息元素，一次性格式化输出
- 避免了重复格式化导致的空行累积

### 实现内容

- 重构saveToFile()方法，采用与群聊消息相同的策略
- 每次重新构建整个XML文档，而不是在现有文档基础上添加
- 添加getAllMessagesFromCache()辅助方法从缓存获取所有消息
- 确保消息按ID排序，保持正确的时间顺序

### 文件修改

**1. com/chat/server/MessageStorage.java**

- 第234-304行：完全重写saveToFile()方法
- 第286-304行：新增getAllMessagesFromCache()辅助方法
- 采用重新构建XML文档的策略，避免空行累积

## 功能3：消息存储结构重构

### 实现内容

- 重新组织消息存储结构，让文件名更清晰地反映其内容
- 将原有的messages.xml拆分为两个专用文件
- 保持群聊消息继续存储在group_xxx.xml文件中

### 存储结构变更

**修改前**：

- `messages.xml` - 混合存储私聊消息和公共聊天消息
- `group_xxx.xml` - 存储指定群聊消息

**修改后**：

- `messages_private.xml` - 仅存储私聊消息 (type=PRIVATE)
- `messages_public.xml` - 仅存储公共聊天消息 (type=GROUP, receiver=null)
- `group_xxx.xml` - 存储指定群聊消息 (type=GROUP, receiver=群名)

### 文件修改

**1. com/chat/server/MessageStorage.java**

- 第21-22行：修改文件常量定义
  - `MESSAGE_FILE` → `PRIVATE_MESSAGE_FILE` 和 `PUBLIC_MESSAGE_FILE`
- 第63-73行：重构saveMessage()存储逻辑
  - 私聊消息 → savePrivateMessageToFile()
  - 公共消息 → savePublicMessageToFile()
  - 群聊消息 → saveGroupMessageToFile()
- 第192-276行：重构loadMessagesFromFile()方法
  - 分别加载私聊和公共消息文件
  - 统计总消息数量
- 第278-342行：新增专用保存方法
  - savePrivateMessageToFile() 和 savePublicMessageToFile()
  - saveMessageToFile() 通用保存方法
- 第344-361行：新增getMessagesFromCacheByType()方法
- 第465-485行：修改createEmptyMessageFile()支持指定文件名

### 消息分类逻辑

| 消息类型 | 触发方式           | 存储位置                 |
| -------- | ------------------ | ------------------------ |
| 私聊消息 | `/p 用户名 消息` | `messages_private.xml` |
| 公共聊天 | 直接输入消息       | `messages_public.xml`  |
| 指定群聊 | `/gs 群名 消息`  | `group_群名.xml`       |
