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

你的项目已经有了小组相关的包（com.chat.NewFunctions.subgroup），但目前SubGroupManager、SubGroupRouter、InvitationService等类还未实现具体功能。Group.java也未包含小组（SubGroup）相关字段和方法。
下一步服务端设计建议：
在com.chat.NewFunctions.subgroup下新建SubGroup数据结构（如SubGroup.java），包含小组ID、名称、成员、所属群等。
在Group.java中添加List<subgroup>字段，管理本群所有小组，并提供增删查改方法。</subgroup>
在SubGroupManager中实现小组的创建、邀请、加入、退出、查找等静态方法，供服务端调用。
在ClientHandler.java中注册小组相关命令（如/sg_create、/sg_invite、/sg_join、/sg_exit、/sg_msg），并调用SubGroupManager的方法。
请确认是否同意此设计，或指定优先实现哪一部分（如SubGroup数据结构、Group集成、命令处理等），我将为你分步实现。


一个模块一个模块完整的进行开发会好很多