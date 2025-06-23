com.chat.client.NewFunctions/
├── audio/              # 语音功能核心
│   ├── VoiceChatManager.java   # 语音会话管理
│   ├── AudioEncoder.java       # 音频编码（使用JAVE）
│   └── Packetizer.java         # 音频数据分片传输
│
├── image/              # 图片功能
│   ├── ImageSender.java        # 图片压缩/发送
│   ├── ImageReceiver.java      # 图片接收/显示
│   └── ThumbnailGenerator.java # 缩略图生成
│
├── subgroup/           # 群内小组功能
│   ├── SubGroupManager.java    # 子群生命周期管理
│   ├── InvitationService.java  # 邀请处理
│   └── SubGroupRouter.java     # 子群消息路由
│
├── shared/             # 共享工具
│   ├── BinaryProtocol.java     # 二进制协议封装
│   └── FileChunker.java        # 文件分块传输（供语音/图片共用）
│
└── test/               # 单元测试
├── AudioTest.java
├── ImageTest.java
└── SubGroupTest.java

开发进度：
目前已经完成audio软件包的基本实现
下一步就是弄清楚代码原理
和如何配合客户端进行使用
并进行相关demo测试判定是否能完成

一个模块一个模块完整的进行开发会好很多