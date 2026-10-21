---
title: '从零开始学 Netty'
subtitle: '网络基础、可运行示例与协议实战'
author: 'Aias00'
lang: zh-CN
rights: '本书为原创教学整理；Netty 名称与所引用资料的权利归各自权利人所有。'
date: '2026-10-21'
---

# 写给第一次接触 Netty 的你

如果你还分不清服务器、端口和线程，可以从这里开始。本书先解释程序为什么需要网络通信，再带你运行一个小服务，最后理解 Netty 在背后做了什么。你不需要一开始就掌握高并发，也不用背下所有类名。

Netty 是 Java 的异步、事件驱动网络应用框架。你可以用它搭建 TCP 服务、HTTP 服务、WebSocket 服务，以及自定义协议的客户端和服务器。它提供网络基础设施；业务规则仍然需要你设计。

本书的终点很具体：你能够运行一个支持中文的行协议服务，解释消息如何经过 Pipeline，知道如何处理半包、内存和连接关闭，并能用测试证明协议在分片到达时仍然正确。

## 怎么使用这本书

先读第 1～4 章并运行示例；第 5～10 章解释机制；第 11～14 章讲测试、工程化和排错；最后是学习计划、答案、速查表与资料来源。第一次遇到不熟悉的术语，可以先看例子，再回头看定义。

每章都有一个小练习。先自己回答，再到附录对照。你也可以只做一件事：每次改动代码之前，预测输出；运行之后，解释差异。

本书使用 Java 17 语法、Maven 和 Netty **4.1.139.Final**。实际验证使用 JDK 21。4.2 是另一条正在发展的版本线，不应把两条版本线的配置片段随意混用。版本信息依据 2026 年 10 月 20 日取得的官方页面与 Maven Central 元数据，后续可能变化。[1][7]

所有主线代码均附在本书末尾，也随书提供完整 Maven 工程。电子书中输入代码比较麻烦时，可以直接使用工程目录。代码按教学目的设计，服务默认只监听本机 `127.0.0.1`。

# 第 1 章　先认识网络通信

## 1.1 两个程序怎样找到对方

把服务器理解为“等待请求并处理请求的程序”，客户端是“主动发起请求的程序”。这两种身份取决于它们在一次通信中的角色，同一个程序也可以同时充当二者。

IP 地址帮助定位网络中的主机，端口帮助操作系统把数据交给某个监听程序。`127.0.0.1:8080` 的含义是：当前这台机器的 8080 端口。`localhost` 通常指向本机，但可能解析为 IPv4 或 IPv6；本书明确使用 IPv4 地址，减少初学时的变量。

一个 TCP 连接通常用源 IP、源端口、目标 IP、目标端口这四个量区分。客户端也有端口，只是通常由操作系统临时分配。服务器的监听端口不能无限重复占用；启动两份相同服务时常会看到地址已被使用的错误。

## 1.2 TCP 提供什么

TCP 提供可靠、有序的字节流。正常建立连接后，如果对端应用持续读取，你发送的字节会按顺序交给它；发生无法恢复的网络故障时，应用需要处理失败或超时。

这里最容易误解的是“字节流”。假设客户端连续发送：

```text
hello\n
world\n
```

服务器可能一次读到 `hello\nworld\n`，也可能分三次读到 `he`、`llo\nwo`、`rld\n`。一次发送和一次接收没有一一对应关系。TCP 不知道哪个字节代表“一条业务消息”。

所以协议必须约定边界。本书前半部分规定：一条消息以换行符结束，内容采用 UTF-8，正文最长 1024 字节。换行符在 Java 字符串中写成 `\n`。在实际网络中发送的是换行字节，而不是反斜线与字母 n 两个字符。

## 1.3 UDP、HTTP 与 Netty 的位置

UDP 保留数据报边界，但不保证送达和有序，适合能自行容忍或补偿丢包的场景。HTTP 是应用层协议；常见的 HTTP/1.1 和 HTTP/2 通常运行在 TCP 之上，HTTP/3 则基于 QUIC。不能把“HTTP”和“TCP”当成同层替代品。

Netty 可以处理多种传输与协议。学 Netty 时先从 TCP 开始，是因为更容易直接看到连接、字节和消息边界之间的关系。

**动手想一想：** 客户端连续调用两次发送，为什么服务器不一定收到两条完整消息？

# 第 2 章　补齐最少的 Java 与工具知识

## 2.1 哪些 Java 知识需要先会

你需要认识类、对象、方法、接口、异常和泛型。不要求熟练，但至少看懂下面的意思：

```java
String message = "你好";
System.out.println(message);
```

`String` 是类型，`message` 是变量，`println` 把内容输出到终端。`new` 创建对象；`@Override` 表示这里实现或覆盖了父类型的方法。`try/finally` 表示无论正常结束还是发生异常，都执行清理代码。

`SimpleChannelInboundHandler<String>` 中的 `<String>` 是泛型参数，表示该处理器接收字符串类型的入站消息。不要被类名长度吓到：本书会逐段解释它负责的工作。

## 2.2 安装并检查 JDK 与 Maven

安装 JDK 17 或 21，以及 Maven 3.9.x。Windows 可以使用安装程序或包管理器；macOS 和 Linux 可以使用对应的包管理器。确保安装的是 JDK，而不只是 JRE。

打开终端，执行：

```sh
java -version
javac -version
mvn -version
```

三条命令都应该显示版本。`mvn -version` 还会列出 Maven 实际使用的 Java。若它与 `java -version` 不同，优先检查 `JAVA_HOME` 与 `PATH`。`JAVA_HOME` 应指向 JDK 根目录，不能指向 `bin/java` 文件。

本书命令在 macOS/Linux shell 中展示。Windows PowerShell 中的 `mvn` 命令相同；路径分隔符可调整为反斜线。遇到路径中有空格，应加引号。

## 2.3 Maven 帮我们做什么

Maven 会根据 `pom.xml` 下载依赖、编译源码和运行测试。项目主要目录如下：

```text
examples/
  pom.xml
  src/main/java/book/LineServer.java
  src/main/java/book/LineClient.java
  src/test/java/book/ProtocolTest.java
```

`src/main/java` 放正式代码，`src/test/java` 放测试；`book` 是 Java 包名，也对应目录层级。`target` 是构建结果目录，可重新生成。

Netty 由多个模块组成。入门工程使用 `netty-all` 聚合依赖，减少手工挑选模块的负担。现代版本的 `netty-all` 不应被理解为“一个包含全部类的大 jar”；Maven 会解析它依赖的模块。正式项目可按需要选择 `netty-transport`、`netty-codec` 等模块，并保持版本一致。

**小练习：** 为什么不能只把下载到的一个很小的 `netty-all` jar 放进项目，就认定所有依赖都齐了？

# 第 3 章　运行第一个 Netty 服务

## 3.1 先让它跑起来

进入随书工程的 `examples` 目录。执行：

```sh
mvn test
mvn compile exec:java -Dexec.mainClass=book.LineServer
```

第一次下载依赖需要联网。测试完成后启动服务器，看到以下文字说明监听已成功：

```text
LISTENING 8080
```

保留这个终端，再打开第二个终端，进入同一个 `examples` 目录：

```sh
mvn compile exec:java -Dexec.mainClass=book.LineClient
```

客户端应依次输出：

```text
收到：你好，Netty
再见
```

客户端先发送一条普通消息，再发送 `quit`。服务器写出“再见”，待这次写操作完成后关闭连接。关闭的是这条客户端连接，服务器仍然继续监听。

在服务器终端按 Ctrl+C 可以停止进程。示例中的 `finally` 负责程序正常退出该代码路径时清理资源；它不是完整的进程信号停机方案，第 13 章会讨论生产服务的停机顺序。

## 3.2 用不同端口试一次

如果 8080 已被占用，两个终端都改用 9090：

```sh
mvn compile exec:java -Dexec.mainClass=book.LineServer -Dexec.args=9090
mvn compile exec:java -Dexec.mainClass=book.LineClient -Dexec.args=9090
```

这两条命令分别在两个终端执行，先运行服务器。客户端参数也必须改，否则它仍会寻找 8080。

## 3.3 服务器的骨架

主线结构如下，完整可复制源码在附录 A：

```java
EventLoopGroup boss = new NioEventLoopGroup(1);
EventLoopGroup worker = new NioEventLoopGroup();
try {
    Channel server = new ServerBootstrap()
        .group(boss, worker)
        .channel(NioServerSocketChannel.class)
        .childHandler(/* 为每条新连接安装处理器 */)
        .bind("127.0.0.1", 8080).sync().channel();
    server.closeFuture().sync();
} finally {
    boss.shutdownGracefully().sync();
    worker.shutdownGracefully().sync();
}
```

这段是结构说明，含有占位注释，运行时请用附录完整版本。`boss` 接收新连接，`worker` 处理已建立连接的 I/O。`ServerBootstrap` 负责组装启动配置；`bind` 发起监听；`sync` 等待异步操作完成；`closeFuture` 在监听 Channel 关闭时完成。

`NioServerSocketChannel` 表示基于 Java NIO 的监听通道。`childHandler` 配置的是接入后的连接。不要与 `handler` 混淆：服务器上的 `handler` 面向监听通道，`childHandler` 面向接受到的子通道。[1]

## 3.4 先形成一个整体图像

```text
客户端发送字节
       ↓
操作系统接收 → worker 的 EventLoop
       ↓
按行切分 → UTF-8 解码 → 业务处理
                          ↓
客户端接收 ← 网络发送 ← UTF-8 编码
```

如果你现在只记住“Netty 把网络事件交给我写的处理器”，就已经足够继续。

**小练习：** 客户端运行结束后，服务器为什么没有跟着退出？

# 第 4 章　Channel、EventLoop 与异步结果

## 4.1 Channel 是连接的操作入口

在本书 TCP 示例中，客户端 Channel 可以理解为连接的操作入口，包含远端地址、是否活动、读写操作和 Pipeline。服务器监听 Channel 的职责不同：它接受新连接。

你可以调用 `channel.writeAndFlush(message)` 请求发送消息，但该方法返回时，不代表对方的业务已经处理完成。它返回一个 `ChannelFuture`，用来观察该次异步操作何时成功或失败。

## 4.2 EventLoop 是谁

EventLoop 负责处理注册到它上面的 Channel 事件和任务。通常一个 Channel 的 I/O 事件由同一个 EventLoop 线程处理，一个 EventLoop 可以管理多条连接。

因此，“有一万条连接就必须有一万个线程”并不成立。Netty 可以让有限的线程处理许多连接；但如果你在这个线程里执行耗时工作，同线程管理的其他连接也会被拖慢。

把 EventLoop 想成负责一排窗口的工作人员：每个窗口有事就处理一小步。若这个人突然花十秒钟搬货，其他窗口都得等。异步模型的价值来自快速处理事件，并让等待交给合适的机制。

## 4.3 sync 与监听器

启动线程中等待 `bind().sync()` 是为了确认监听结果。事件回调中则应避免对尚未完成的 Future 调用阻塞等待；这样可能阻塞 EventLoop，甚至形成死锁。

观察发送结果可以写成：

```java
ctx.writeAndFlush("hello\n").addListener(future -> {
    if (!future.isSuccess()) {
        future.cause().printStackTrace();
        ctx.close();
    }
});
```

Future 成功通常说明这次出站操作在本地传输层完成，不能证明对方程序读到、保存到数据库或提交了事务。业务上需要“确认处理成功”，必须设计应答消息、请求编号和超时。

## 4.4 不要在回调里做的事

直接 `Thread.sleep`、同步访问数据库、调用慢 HTTP 接口、读取大文件，都会占用当前处理器所在线程。如果它是 EventLoop，就会影响其他连接。第 12 章给出把业务处理移到独立线程池的方式。

**小练习：** 一个写操作 Future 成功，能否证明订单已经入库？需要什么额外约定？

# 第 5 章　Pipeline：消息的处理路线

## 5.1 本书使用的四个处理器

每条连接建立时，我们安装：

```java
ch.pipeline().addLast(
    new LineBasedFrameDecoder(1024),
    new StringDecoder(CharsetUtil.UTF_8),
    new StringEncoder(CharsetUtil.UTF_8),
    new ReplyHandler());
```

`LineBasedFrameDecoder` 从字节流中找出以换行结束的完整帧，默认移除行分隔符。`StringDecoder` 把完整帧的 UTF-8 字节转换为 Java 字符串。`ReplyHandler` 根据字符串生成回答。`StringEncoder` 在发送时把字符串转换为字节。

入站事件大体沿加入顺序前进，出站事件大体沿反方向前进；只实现了相应方向接口的处理器才处理该方向的事件。[2]

```text
加入顺序： 行解码器 → 字符串解码器 → 字符串编码器 → 业务处理器
入站方向： ──────────────────────────────────→
出站方向： ←──────────────────────────────────
```

把编码器放在业务处理器之前，是为了让业务处理器通过 `ctx.writeAndFlush` 发出的字符串，在出站传播时能遇到编码器。

## 5.2 ctx 与 channel 的起点不同

`ctx` 是当前处理器的上下文。`ctx.write` 从当前上下文向前寻找出站处理器；`channel.write` 从 Pipeline 尾部发起出站传播。处理器位置改变时，两种写法可能走过不同的处理器集合。

同样，入站处理器要把事件交给后面的处理器，可以调用 `ctx.fireChannelRead(msg)`。如果既不转发也不处理消息，消息就在这里停止。涉及 ByteBuf 时，还必须考虑谁释放它。

## 5.3 SimpleChannelInboundHandler 的便利与边界

本书业务处理器继承 `SimpleChannelInboundHandler<String>`，实现 `channelRead0`。它默认会在回调结束后自动释放匹配类型的引用计数消息。这里接收的是 String，没有 ByteBuf 的释放负担。

如果改成 `SimpleChannelInboundHandler<ByteBuf>`，回调结束后默认自动释放收到的 ByteBuf。把这个对象存起来稍后使用，或原样写出去时，必须先明确引用计数的所有权，不能照搬字符串写法。

## 5.4 一个 Handler 对象能给所有连接共用吗

带连接状态的处理器通常应每连接创建一个实例。`ChannelInitializer` 中 `new ReplyHandler()` 正是这样做的。只有确认处理器可安全共享，并符合 Netty 的共享约定时，才使用 `@Sharable`。一个处理器可能在多条连接的不同线程上被调用，普通成员变量不自动变成线程安全。

**小练习：** 若把 `StringEncoder` 放到业务处理器之后，业务里仍使用 `ctx.writeAndFlush`，会有什么风险？

# 第 6 章　拆包、粘包与协议边界

## 6.1 不要猜一次 read 的长度

“粘包”通常指一次读取包含多条消息，“半包”指一次读取只有消息的一部分。它们是字节流读取的正常现象。不能靠延时、一次发送后睡眠、关闭 Nagle 算法来建立可靠的业务边界。

常见方案如下：

| 方案 | 如何找边界 | 适用例子 | 主要限制 |
|---|---|---|---|
| 固定长度 | 每 N 字节一条 | 简单设备协议 | 变长内容浪费空间 |
| 分隔符 | 找到约定结束符 | 文本命令 | 正文须转义或禁止分隔符 |
| 长度字段 | 先读头部，再读指定长度 | 二进制协议 | 长度验证必须严格 |
| 更高层协议 | 使用已有标准编解码器 | HTTP、WebSocket | 须遵守标准的完整语义 |

## 6.2 为什么先切帧再解码中文

UTF-8 中一个中文字符常占 3 个字节，但 TCP 可能在任意字节位置分开。如果把每次收到的 ByteBuf 独立转成字符串，可能把一个字符的前半截当成错误序列。

本书先由行解码器积累完整帧，再由字符串解码器一次转换。测试专门把“你”的第一个字节与其余字节拆开送入，验证结果仍为“你”。这说明测试要模拟网络允许的行为，不能只验证最理想的整包输入。

## 6.3 长度字段协议的例子

假设一条帧由 4 字节长度和 N 字节正文组成。长度字段使用大端序，表示正文长度，不包含自己的 4 字节。

```text
00 00 00 03 | 61 62 63
正文长度 3 |   a  b  c
```

可以使用：

```java
new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4)
```

五个参数分别表示：最大帧总长度 1024；长度字段偏移量 0；字段宽度 4；额外调整值 0；解码后跳过前 4 字节。因为长度值已经表示正文长度，解码器会结合长度字段末端位置算出整帧长度。这里允许的正文最大值是 1020，而不是 1024。[3]

发送方向常搭配 `LengthFieldPrepender(4)`，它默认将正文长度作为 4 字节头加到前面。若业务发字符串，Pipeline 可按以下加入顺序组装：

```text
LengthFieldBasedFrameDecoder
StringDecoder
LengthFieldPrepender
StringEncoder
业务处理器
```

从业务处理器通过 `ctx.writeAndFlush` 发出字符串后，出站先把字符串变为字节，再在字节前添加长度。不要再添加换行符，除非新协议明确要求它。

## 6.4 限制长度是协议的一部分

如果允许客户端发送无限长、永远没有结束符的内容，服务端可能持续积累内存。必须限定帧长，并定义超限后的行为。示例的异常处理器会记录异常并关闭连接。

行解码器的默认超限报告时机与显式启用 `failFast` 时不同。测试采用 `new LineBasedFrameDecoder(8, true, true)`，明确要求发现超限时尽早报错，避免把实现细节当成普遍保证。

**小练习：** 长度字段值为 12、头部为 4 字节时，线上整帧有多少字节？若最大帧长为 1024，正文最多多长？

# 第 7 章　ByteBuf 与内存所有权

## 7.1 ByteBuf 里有两个重要下标

ByteBuf 是 Netty 的字节容器。可以先把它理解为带读写位置的字节数组：[4]

```text
0             readerIndex          writerIndex        capacity
| 已读区域       | 可读区域             | 可写区域            |
```

`readableBytes()` 表示当前可读字节数。`readInt()` 读取 4 字节并移动读下标；`getInt(index)` 读取指定位置而不移动读下标。`writeInt` 写入并推进写下标。

读取前必须确认有足够字节。如果自己实现 `ByteToMessageDecoder`，当数据不足时应直接返回，并且不要提前消耗无法恢复的头部。必要时使用绝对读取检查长度，完整后才移动下标。

## 7.2 为什么需要 release

部分 ByteBuf 使用直接内存或内存池，生命周期不能只依赖 Java 垃圾回收。引用计数用于表示还有多少使用者持有它。`retain()` 增加计数，`release()` 减少计数；到 0 后对象不可再用。[5]

最实用的原则是：**接收并消费消息的一方负责释放；把消息转交给下一环节时，通常同时转交释放责任。** 具体仍要服从所用处理器和 API 的契约。

在普通 `ChannelInboundHandlerAdapter` 中消费 ByteBuf，可以这样写：

```java
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    try {
        ByteBuf buf = (ByteBuf) msg;
        System.out.println(buf.readableBytes());
    } finally {
        ReferenceCountUtil.release(msg);
    }
}
```

这是额外示意代码，需要导入对应类型。如果调用 `ctx.fireChannelRead(msg)` 转发原对象，通常不应在同一路径再释放一次。若使用默认自动释放的 `SimpleChannelInboundHandler<ByteBuf>`，则不要在回调结束时额外手动释放同一份所有权。

## 7.3 slice、duplicate 和 copy

`slice()` 与 `duplicate()` 通常共享底层内容，也不自动增加引用计数。源缓冲区释放后，派生视图不能凭空继续使用。需要独立延长生命周期时，可按契约使用 `retainedSlice()`、`retainedDuplicate()`，并在使用结束时释放。

`copy()` 复制内容，得到独立的缓冲区，代价是分配与复制。初学时先追求清楚的所有权，再考虑减少复制。

## 7.4 测试中的 ByteBuf 也要释放

`EmbeddedChannel.readOutbound()` 取出的 ByteBuf 已经交给测试代码。测试读取后应在 `finally` 中 `release()`。测试结束调用 `finishAndReleaseAll()`，清理仍留在通道里的消息；已经被取走的对象仍由取走者负责。

遇到 `IllegalReferenceCountException`，检查是否重复释放或释放后使用。遇到泄漏告警，检查异常分支、消息丢弃分支和跨线程转交分支。开发排查时可打开更严格的泄漏检测，但它会增加开销。

**小练习：** 在 `SimpleChannelInboundHandler<ByteBuf>` 中，为什么不能直接把收到的 buf 存到列表里，等一分钟后再读？

# 第 8 章　连接生命周期与正确关闭

## 8.1 常见事件的意思

| 事件 | 可以先这样理解 |
|---|---|
| `channelRegistered` | Channel 注册到 EventLoop |
| `channelActive` | 连接进入活动状态 |
| `channelRead` / `channelRead0` | 收到一条进入当前处理器的消息 |
| `channelReadComplete` | 当前一轮读事件结束，不代表业务消息结束 |
| `channelInactive` | 连接变为非活动状态 |
| `exceptionCaught` | 处理传播过来的异常 |

握手、鉴权等上层状态不一定与 `channelActive` 相同。例如 TCP 已连接，不代表 TLS 握手成功，更不代表用户已登录。

## 8.2 为什么先发送，再在监听器里关闭

示例对 `quit` 的处理为：

```java
ctx.writeAndFlush("再见\n")
   .addListener(ChannelFutureListener.CLOSE);
```

它表达“这次写操作完成后关闭”。如果先调 `writeAndFlush` 再立即调 `close`，就可能过早关闭，影响待发送的数据。即使监听器触发，也不能据此证明对方业务已消费这条消息。

`write` 是把消息交给出站处理过程，`flush` 请求把待写内容推进到底层。高吞吐场景可以批量 write 后再 flush；初学示例每次直接使用 `writeAndFlush`，便于观察。

## 8.3 断线不等于立即可见

拔网线、设备休眠、对端崩溃等情况下，本地不一定马上收到明确的关闭通知。应用要按业务要求设计空闲检测、请求超时、心跳和重连。

重连不是简单死循环：应设置退避、随机抖动、最大频率与退出条件；断线时已发送但未获应答的请求处于不确定状态。若要重试修改数据的操作，需要幂等键或去重机制。

**小练习：** `channelReadComplete` 能否当作“收到一条完整订单”的判断条件？

# 第 9 章　空闲检测、心跳与超时

## 9.1 IdleStateHandler 不会自动发送心跳

`IdleStateHandler` 按读、写或读写活动检测空闲，并向后传播 `IdleStateEvent`。它本身不会替你规定 PING/PONG 协议，也不会自动完成鉴权或判定业务健康。[6]

例如：

```java
pipeline.addLast(new IdleStateHandler(30, 0, 0));
```

这个构造形式的时间单位为秒，表示一段时间未读到数据时触发读空闲事件。若它放在解码器前，读活动主要体现网络字节到达；放在某些解码器后，观察到的可能是解码后的消息活动。应根据目标定义它的位置。

## 9.2 先定义协议，再写处理器

可以约定客户端每隔一定时间发送 `PING\n`，服务端回答 `PONG\n`。客户端如果连续超时未得到响应，关闭连接并进入重连流程。服务端若长时间无入站数据，也可关闭空闲连接。

注意：本书默认的 ReplyHandler 尚未实现 PING/PONG，它会把 PING 当作普通文本回复。实现心跳需要增加对应分支，并写测试。

当处理空闲事件时，可以在 `userEventTriggered` 中检查 `evt` 是否是 `IdleStateEvent`。若不是本处理器负责的事件，应调用 `ctx.fireUserEventTriggered(evt)` 继续传播。

## 9.3 三类时间不要混用

连接超时控制“建立连接等多久”；读空闲控制“一段时间没有读到活动”；请求超时控制“某个请求等应答多久”。它们解决的不是同一个问题。

一个连接可能一直有心跳，却有某个业务请求永远没有完成。因此请求超时需要按请求 ID 单独管理，并在完成或失败后清除定时任务和待处理表。

**小练习：** 每秒都收到心跳，是否可以取消所有业务请求超时？

# 第 10 章　从行协议走向自定义协议

## 10.1 先写一份人能读懂的协议说明

在写编解码器前，定义字段与约束。例如：

| 字段 | 大小 | 约定 |
|---|---:|---|
| magic | 2 字节 | 固定值，帮助拒绝明显错误输入 |
| version | 1 字节 | 协议版本 |
| type | 1 字节 | 请求、应答、心跳等 |
| requestId | 8 字节 | 关联请求与应答 |
| bodyLength | 4 字节 | 正文字节数，大端序 |
| body | 可变 | UTF-8 JSON 或约定的二进制格式 |

以上是设计练习，主线工程没有实现这个二进制协议。不要把这张表误认为可以直接与行协议客户端通信。

## 10.2 从字段表推出帧参数

头部总长为 16 字节，bodyLength 位于偏移 12，宽度 4，表示正文长度。若希望保留整个头部供后续解码器读取，可以使用：

```java
new LengthFieldBasedFrameDecoder(1048576, 12, 4, 0, 0)
```

长度字段末端正好是头部末端，因此调整值为 0；最后一个 0 表示不剥离头部。最大整帧为 1 MiB，正文最多为 1 MiB 减 16 字节。

之后的业务消息解码器应校验 magic、版本、type 和长度，再解析正文。字段值不可信，不能因为它来自已建立的 TCP 连接就跳过校验。

## 10.3 请求应答关系

客户端为请求分配 requestId，保存待完成结果；服务端应答携带相同编号。应答到达后找到对应请求并完成它；超时后删除记录，避免内存一直增长。

请求编号必须在尚未完成的请求范围内保持唯一。断线时要统一失败化尚未完成的请求，否则调用方可能永远等待。若发生重连，还要考虑旧连接的应答与新连接请求之间的隔离。

## 10.4 序列化与安全边界

JSON 便于初学和调试，但仍须限制正文大小、嵌套深度和字段范围。不要为了省事，把不可信网络输入交给 Java 原生对象反序列化。

公网传输敏感信息时，应使用 TLS，并在客户端验证证书和目标主机身份。加密、认证、授权是不同的问题：建立加密连接，不等于请求者有操作数据的权限。

**小练习：** 为什么只有 requestId，还不足以保证断线重试不会重复扣款？

# 第 11 章　用测试模拟真实网络

## 11.1 为什么不能只手工运行一次

手工看到“收到：你好”只能证明某次输入成功。它没有证明分片、合并、超长输入和关闭时机都正确。网络相关程序尤其需要构造边界条件。

Netty 的 `EmbeddedChannel` 允许在内存中运行 Pipeline，不需要占用真实端口。向 `writeInbound` 喂字节，检查 `readOutbound` 得到的响应，就能测试编解码器和业务处理器的组合。

## 11.2 最关键的测试过程

```java
ch.writeInbound(Unpooled.copiedBuffer("he", CharsetUtil.UTF_8));
assertNull(ch.readOutbound());
ch.writeInbound(Unpooled.copiedBuffer("llo\nworld\n", CharsetUtil.UTF_8));
```

第一步不应产生回复，因为还没遇到换行。第二步应产生两条响应，分别对应 hello 和 world。这样一次同时覆盖了消息分片与消息合并。

注意：`writeInbound` 返回值表示是否有消息留在入站队列供读取，不是“网络读取是否成功”。本例业务处理器消费了入站消息并生成出站消息，所以判断业务结果应读取出站队列。

## 11.3 随书测试覆盖什么

工程包含五项测试：普通文本的分片与合并；UTF-8 字符内部的分片；quit 的响应与关闭；超长行的拒绝；长度字段帧的分片重组。测试源码见附录 A。

这组测试支持教学示例的主要行为，但没有验证大规模并发、TLS、网络抖动下的长期运行或生产吞吐能力。测试结论必须限定在实际覆盖的范围内。

## 11.4 下一步可以增加什么

你可以增加：空行、CRLF、刚好等于长度上限、断线时残缺帧、连续多条消息、错误 UTF-8、连接失败。若业务要求严格拒绝非法 UTF-8，需要显式设计严格解码策略；普通字符串转换不应被当作输入合法性校验。

真实端到端测试则要启动服务器，建立 Socket 连接，发送字节并验证响应。它验证启动配置与网络路径，而 EmbeddedChannel 更适合精确测试 Pipeline 的边界行为。两者相互补充。

**小练习：** 为什么“整条中文一次输入能通过”还不足以证明中文处理正确？

# 第 12 章　慢业务、线程池与背压

## 12.1 把慢操作移出 EventLoop

假设收到一行后要同步访问数据库。可以给业务处理器指定单独的执行器组，例如：

```java
DefaultEventExecutorGroup business = new DefaultEventExecutorGroup(4);
// 初始化每条连接时：
pipeline.addLast(business, "business", new MyBusinessHandler());
```

这段是线程模型示意，`MyBusinessHandler` 需要你实现，并导入 `io.netty.util.concurrent.DefaultEventExecutorGroup`。执行器组应在服务级创建并复用，不能每条连接创建一组，停机时也要关闭。

把整个处理器交给这种执行器组，与在回调里临时向普通线程池提交每个请求，顺序语义不同。若自己把请求并行提交，后发请求可能先完成；需要按协议决定是否允许乱序，以及怎样关联应答。

## 12.2 换线程不等于容量无限

业务线程池、待处理任务、数据库连接池都有限。若接收速度长期大于处理速度，队列会增长，内存和延迟都会恶化。

应明确容量和拒绝策略：队列满时返回繁忙、限制读入、拒绝新请求或关闭连接。对于会修改数据的请求，还要明确“拒绝”发生在执行前还是执行后，避免客户端误判。

## 12.3 关注发送队列

当客户端读取很慢时，服务端出站数据可能积压。`channel.isWritable()` 是基于写缓冲水位等状态的信号，不是对方能否处理业务的证明，也不是硬性的内存上限。

可以监听 `channelWritabilityChanged`，在不可写时暂停产生更多消息，恢复可写后继续。仅仅忽略 isWritable 并持续发送，仍可能造成内存压力。设置水位并不会自动替业务拒绝所有后续写入。

## 12.4 自动读取开关

`autoRead` 可以影响是否自动继续读入。更复杂的服务会结合高低水位暂停与恢复读取。但暂停读取时必须有可靠的恢复路径，否则连接可能一直卡住。初学阶段先把帧长限制、业务队列容量与超时做好，再逐步增加流控。

**小练习：** 为什么把数据库查询放进线程池后，仍然可能出现内存不断增长？

# 第 13 章　HTTP、TLS 与工程化边界

## 13.1 使用标准协议时，优先使用标准编解码器

处理 HTTP/1.x 通常从 `HttpServerCodec` 开始。若希望把分片 HTTP 内容聚合成完整请求，可以再使用有明确大小上限的 `HttpObjectAggregator`。聚合方便开发，但意味着要在内存中保存整个消息，上传大文件时更应考虑流式处理。

WebSocket 通常先经 HTTP 握手，再升级协议；可研究 `WebSocketServerProtocolHandler`。WebSocket 帧有边界与控制帧语义，不能把 TCP 行协议的逻辑原样套过去。

本章给出方向，不包含完整 HTTP/WebSocket 工程。主线教学工程始终使用 TCP 行协议，避免在一个入门项目里同时引入太多状态。

## 13.2 TLS 放在什么位置

TLS 处理通常放在 Pipeline 靠前位置，使后续协议处理器接触解密后的应用数据，出站则先编码再加密。可从 `SslContextBuilder` 与 `SslHandler` 的官方说明继续学习。

本机练习可以先明文运行。部署时要根据实际访问范围配置证书、密钥保护、身份验证和权限控制；不能把用于测试的信任所有证书配置复制到正式客户端。

## 13.3 怎样有序停止服务

一个生产服务常见的停机顺序是：停止接受新连接；停止接收新业务请求；等待进行中的业务在限定时间内结束；关闭现有连接；关闭业务线程池；关闭 EventLoopGroup。

本书主线代码便于阅读，只展示了 EventLoopGroup 的清理。若扩展为常驻服务，应跟踪监听通道、子连接和业务任务，并接入应用生命周期或 shutdown hook。关闭要有期限，不能因为一条慢请求让停机无限等待。

## 13.4 日志与指标

日志至少要能关联连接、请求编号和异常原因。不要直接把口令、令牌与完整敏感正文写入日志。网络日志处理器能帮助观察字节，但在高流量下会产生大量日志和额外开销。

值得观察的指标包括：活动连接数、连接建立失败数、请求延迟、业务队列长度、超时次数、出站积压和直接内存使用情况。先通过指标确认瓶颈，再考虑调整线程数、TCP 参数或内存分配器。

**小练习：** 为什么服务端把消息写成功的次数，不能直接当作业务成功次数？

# 第 14 章　遇到问题时这样排查

## 14.1 先确认问题发生在哪一层

遵循从外到内的顺序：进程是否启动 → 是否监听正确地址和端口 → 客户端是否连通 → 字节是否到达 → 帧是否完整 → 解码是否正确 → 业务是否执行 → 出站编码与发送是否完成。

每一步都要收集证据。比如“客户端没有输出”可能是没连上，也可能是忘记换行、没有 flush，或响应被错误的 Pipeline 顺序挡住。直接增加线程数通常解决不了这些问题。

## 14.2 常见现象速查

| 现象 | 优先检查 | 操作方向 |
|---|---|---|
| `Address already in use` | 端口被占用 | 停止旧进程或双方改端口 |
| `Connection refused` | 服务未启动或端口不对 | 看 LISTENING 与客户端参数 |
| 连接一直超时 | 地址、路由、防火墙 | 先在本机测试，再逐步跨机器 |
| 连上但不回复 | 行分隔符、处理器顺序 | 确认发送实际换行字节 |
| 中文乱码 | 双方字符集、切帧顺序 | 统一 UTF-8，先切帧后转字符串 |
| `TooLongFrameException` | 超过协议限制 | 检查发送内容，合理设置上限 |
| `IllegalReferenceCountException` | 重复释放、释放后使用 | 逐条跟踪所有权 |
| 内存持续上升 | 泄漏、队列积压、大消息 | 区分 ByteBuf 泄漏与活跃对象堆积 |
| 少量请求很慢 | EventLoop 阻塞 | 检查数据库、sleep、文件 I/O |
| 客户端迟迟不结束 | 等不到服务器关闭 | 确认发送 quit 且带换行 |

## 14.3 一个“没有响应”的完整推理

假设服务器显示已经监听，客户端也连接成功，但输入 hello 后没有回复。先确认发送的是 `hello\n` 对应的实际换行，而不是仅仅 `hello`。行解码器会等待边界，因此没有收到完整帧时不回复是正确行为。

若换行已发送，再检查是否把 `StringEncoder` 放到了业务处理器之后；若业务使用 `ctx.writeAndFlush`，该编码器可能没有经过。再检查异常日志，确认不是不支持的出站消息类型。

最后检查对端是否持续读取。写成功、读取成功和业务处理成功要分别观察，不能用某一个日志替代所有层面的证据。

**小练习：** 当所有连接一起变慢，而 CPU 很低时，为什么仍然要检查 EventLoop 中的阻塞调用？

# 第 15 章　两周学习路线与进阶项目

## 15.1 每天 30～60 分钟

| 天数 | 任务 | 完成标准 |
|---|---|---|
| 第 1 天 | 阅读第 1～2 章 | 用自己的话解释端口与字节流 |
| 第 2 天 | 运行服务端和客户端 | 看见两条预期响应 |
| 第 3 天 | 手抄启动代码 | 说清 boss、worker 与 bind |
| 第 4 天 | 阅读 Pipeline | 画出双向处理顺序 |
| 第 5 天 | 修改业务回复 | 预测并验证输出 |
| 第 6 天 | 学习帧边界 | 解释半包与粘包 |
| 第 7 天 | 阅读与运行测试 | 看懂每个断言的目标 |
| 第 8 天 | 学习 ByteBuf | 区分读写下标与引用计数 |
| 第 9 天 | 练习长度字段 | 从协议表推出解码器参数 |
| 第 10 天 | 设计心跳 | 写清超时、应答与关闭规则 |
| 第 11 天 | 理解线程与队列 | 找出哪些操作会阻塞 |
| 第 12 天 | 按排错表演练 | 故意去掉换行并定位原因 |
| 第 13 天 | 实现一个扩展命令 | 添加代码和行为测试 |
| 第 14 天 | 复述完整请求路径 | 从字节到业务再到响应讲一遍 |

如果 Java 基础薄弱，可以把每一天扩展为两到三天。进度的标准是能够解释和复现，不是翻过多少页。

## 15.2 第一个扩展：命令式小服务

在行协议上增加三个命令：`ECHO 内容` 原样返回内容，`TIME` 返回当前时间，`QUIT` 回复后关闭。先写命令大小写、空参数、未知命令和空行的处理规则，再实现。

验收时至少覆盖：一条命令分两次到达；一次输入多条命令；中文内容；未知命令；超长输入；退出应答。不要只测试新命令的正常路径。

## 15.3 第二个扩展：请求编号

约定 `请求编号|命令|参数` 的文本格式，响应保留请求编号。明确参数中是否允许 `|`，若允许，就设计转义或换用成熟序列化格式。

随后让客户端同时发送多个请求，维护待处理表与超时。观察关闭连接时是否清理全部待处理项。这一步能够把“TCP 连接”与“业务请求”真正区分开来。

## 15.4 第三个扩展：聊天服务

为每条连接增加昵称与登录状态，把消息广播给其他连接。要考虑昵称冲突、断线清理、慢客户端、广播消息所有权与房间容量。

先用不可变字符串做广播，可以减少 ByteBuf 所有权问题。共享房间表需要线程安全设计，因为不同连接可能由不同 EventLoop 管理。不要为了复用，把所有连接状态随意塞入一个未同步的 HashMap。

到这里，你已经具备继续阅读官方示例和更复杂协议实现的基础。下一步可以按兴趣学习 HTTP、WebSocket、TLS 或协议设计，不必立即钻进所有底层源码。

# 附录 A　完整工程代码

本附录内容由随书工程源文件直接生成，避免电子书与工程发生手工抄写差异。请按标出的路径保存文件，并保留 `package book;`。项目根目录是 `examples`，所有 Maven 命令都在该目录执行。


## pom.xml

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
<modelVersion>4.0.0</modelVersion><groupId>book</groupId><artifactId>netty-beginner</artifactId><version>1.0.0</version>
<properties><maven.compiler.release>17</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding><netty.version>4.1.139.Final</netty.version></properties>
<dependencies><dependency><groupId>io.netty</groupId><artifactId>netty-all</artifactId><version>${netty.version}</version></dependency><dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><version>5.11.4</version><scope>test</scope></dependency></dependencies>
<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.13.0</version></plugin><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.5.2</version></plugin><plugin><groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId><version>3.5.0</version></plugin></plugins></build></project>
```

## src/main/java/book/LineServer.java

```java
package book;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public final class LineServer {
    public static void main(String[] args) throws InterruptedException {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup();
        try {
            Channel server = new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                            new LineBasedFrameDecoder(1024),
                            new StringDecoder(CharsetUtil.UTF_8),
                            new StringEncoder(CharsetUtil.UTF_8),
                            new ReplyHandler());
                    }
                }).bind("127.0.0.1", port).sync().channel();
            System.out.println("LISTENING " + port);
            server.closeFuture().sync();
        } finally {
            boss.shutdownGracefully().sync();
            worker.shutdownGracefully().sync();
        }
    }

    static final class ReplyHandler extends SimpleChannelInboundHandler<String> {
        @Override protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            if ("quit".equals(msg)) {
                ctx.writeAndFlush("再见\n").addListener(ChannelFutureListener.CLOSE);
            } else {
                ctx.writeAndFlush("收到：" + msg + "\n");
            }
        }
        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}

```

## src/main/java/book/LineClient.java

```java
package book;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public final class LineClient {
    public static void main(String[] args) throws InterruptedException {
        int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Channel ch = new Bootstrap().group(group).channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override protected void initChannel(SocketChannel c) {
                        c.pipeline().addLast(new LineBasedFrameDecoder(2048),
                            new StringDecoder(CharsetUtil.UTF_8),
                            new StringEncoder(CharsetUtil.UTF_8),
                            new SimpleChannelInboundHandler<String>() {
                                @Override protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                                    System.out.println(msg);
                                }
                                @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    cause.printStackTrace(); ctx.close();
                                }
                            });
                    }
                }).connect("127.0.0.1", port).sync().channel();
            ch.writeAndFlush("你好，Netty\nquit\n").sync();
            ch.closeFuture().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }
}

```

## src/test/java/book/ProtocolTest.java

```java
package book;
import io.netty.buffer.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.*;
import io.netty.handler.codec.string.*;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    private EmbeddedChannel channel() {
        return new EmbeddedChannel(new LineBasedFrameDecoder(1024),
            new StringDecoder(CharsetUtil.UTF_8), new StringEncoder(CharsetUtil.UTF_8),
            new LineServer.ReplyHandler());
    }
    private String take(EmbeddedChannel ch) {
        ByteBuf b = ch.readOutbound();
        assertNotNull(b);
        try { return b.toString(CharsetUtil.UTF_8); } finally { b.release(); }
    }
    @Test void splitAndCombinedMessages() {
        EmbeddedChannel ch = channel();
        try {
            ch.writeInbound(Unpooled.copiedBuffer("he", CharsetUtil.UTF_8));
            assertNull(ch.readOutbound());
            ch.writeInbound(Unpooled.copiedBuffer("llo\nworld\n", CharsetUtil.UTF_8));
            assertEquals("收到：hello\n", take(ch));
            assertEquals("收到：world\n", take(ch));
        } finally { ch.finishAndReleaseAll(); }
    }
    @Test void utf8MaySplitInsideCharacter() {
        EmbeddedChannel ch = channel();
        byte[] bytes = "你\n".getBytes(CharsetUtil.UTF_8);
        try {
            ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{bytes[0]}));
            assertNull(ch.readOutbound());
            ch.writeInbound(Unpooled.wrappedBuffer(java.util.Arrays.copyOfRange(bytes,1,bytes.length)));
            assertEquals("收到：你\n",take(ch));
        } finally { ch.finishAndReleaseAll(); }
    }
    @Test void quitFlushesThenCloses() {
        EmbeddedChannel ch = channel();
        try {
            ch.writeInbound(Unpooled.copiedBuffer("quit\n", CharsetUtil.UTF_8));
            assertEquals("再见\n",take(ch));
            assertFalse(ch.isOpen());
        } finally { ch.finishAndReleaseAll(); }
    }
    @Test void oversizedLineRejected() {
        EmbeddedChannel ch = new EmbeddedChannel(new LineBasedFrameDecoder(8, true, true));
        try {
            assertThrows(TooLongFrameException.class, () -> ch.writeInbound(
                Unpooled.copiedBuffer("123456789",CharsetUtil.UTF_8)));
        } finally { ch.finishAndReleaseAll(); }
    }
    @Test void lengthFieldSurvivesFragmentation() {
        EmbeddedChannel ch = new EmbeddedChannel(new LengthFieldBasedFrameDecoder(1024,0,4,0,4));
        try {
            ByteBuf first = Unpooled.buffer().writeInt(3).writeByte(1);
            assertFalse(ch.writeInbound(first));
            assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{2,3})));
            ByteBuf result = ch.readInbound();
            try { assertEquals(3,result.readableBytes());assertEquals(1,result.readByte());assertEquals(2,result.readByte());assertEquals(3,result.readByte()); }
            finally { result.release(); }
        } finally { ch.finishAndReleaseAll(); }
    }
}

```

# 附录 B　练习参考答案

1. **两次发送不一定对应两次接收：** TCP 提供字节流，不保留应用调用 send/write 的边界。接收缓冲区、网络传输与读取时机都会影响每次读到的字节数。需要协议定界。

2. **netty-all 不等于单个全量 jar：** 现代版本通过 Maven 传递依赖带入多个模块。应使用构建工具解析依赖，而不是只复制聚合 jar。

3. **客户端结束而服务端继续：** 客户端连接 Channel 与监听 Channel 不同。关闭一条连接不会自动关闭监听通道，服务器仍可接受其他连接。

4. **写 Future 与入库：** 写操作成功不能证明业务处理成功。应设计带请求编号的业务应答，明确应答是在事务提交前还是提交后发出，并配置请求超时。

5. **编码器位置：** `ctx.writeAndFlush` 从当前上下文向前走出站路线。业务处理器之后的编码器可能被跳过，导致字符串无法正确变成网络字节。

6. **长度字段：** 正文 12 字节加 4 字节头，共 16 字节。整帧上限 1024 时，正文最多 1020 字节。前提是长度字段不包含自身、没有其他头尾字段。

7. **延后使用 ByteBuf：** SimpleChannelInboundHandler 默认在回调结束后释放匹配消息。延后使用需要明确 retain 与 release 配对，或复制所需内容；共享视图不自动延长生命。

8. **readComplete 与完整订单：** 前者表示一轮读取事件结束，不是业务边界。应由协议解码器产出完整订单消息。

9. **心跳不能替代请求超时：** 心跳只能反映某种连接或协议活动，具体业务仍可能卡死。每个请求需要自己的期限与清理逻辑。

10. **requestId 与重复扣款：** requestId 仅用于关联应答，不自动提供幂等。服务端需要稳定的业务幂等键、去重记录或事务约束，并定义重试语义。

11. **中文测试：** 整包输入没覆盖 UTF-8 字符跨读取边界的情况。应在一个多字节字符内部切分输入，再检查最终结果。

12. **线程池仍可能积压：** 进入队列的速度长期高于消费速度时，待处理任务会增长。线程池还可能受数据库连接池和外部服务吞吐限制，需要队列上限与背压。

13. **写成功计数不等于业务成功：** 本地发送、对端读取和业务提交属于不同阶段。指标必须对应真正要衡量的阶段。

14. **CPU 低也可能阻塞：** 等待数据库、锁或网络时线程不消耗很多 CPU，却占着 EventLoop，导致其他连接的事件无法及时处理。

# 附录 C　术语与命令速查

| 术语 | 本书中的含义 |
|---|---|
| Channel | 网络通道的操作入口，监听通道与连接通道职责不同 |
| EventLoop | 执行通道事件与任务的循环及其线程机制 |
| EventLoopGroup | 管理一组 EventLoop |
| Bootstrap | 客户端启动配置器 |
| ServerBootstrap | 服务端启动配置器 |
| Pipeline | 每个 Channel 的处理器链 |
| Handler | 处理某类事件或消息的对象 |
| Context | 处理器在 Pipeline 中的上下文 |
| Future | 可观察异步操作完成状态的结果对象 |
| Frame | 根据协议切出的完整消息单元 |
| ByteBuf | Netty 字节缓冲容器 |
| Backpressure | 消费跟不上时限制继续生产或接收的机制 |
| Idempotency | 相同业务操作重复提交仍保持约定结果的性质 |

常用命令，均在 `examples` 目录执行：

```sh
mvn test
mvn compile exec:java -Dexec.mainClass=book.LineServer
mvn compile exec:java -Dexec.mainClass=book.LineClient
mvn dependency:tree
```

前三条分别用于测试、启动服务端、运行客户端。服务端需要单独终端持续运行。最后一条用于查看实际解析的依赖版本；当出现模块版本不一致时尤其有用。

# 附录 D　资料来源与继续阅读

本书是面向初学者的原创组织与讲解，参考下列官方资料核对概念和 API，不是官方文档的逐段翻译。检索与访问日期：2026 年 10 月 20 日。在线文档会更新；本书示例依赖固定为 4.1.139.Final。

1. **Netty User guide for 4.x**：<https://netty.io/wiki/user-guide-for-4.x.html>。用于核对启动器、EventLoopGroup、Channel、异步结果和基础 TCP 示例。适合完成第一个程序后对照阅读。

2. **ChannelPipeline API**：<https://netty.io/4.1/api/io/netty/channel/ChannelPipeline.html>。用于核对入站与出站方向，以及 Channel、Context 发起事件的起点差异。对应第 5 章。

3. **LengthFieldBasedFrameDecoder API**：<https://netty.io/4.1/api/io/netty/handler/codec/LengthFieldBasedFrameDecoder.html>。用于核对长度字段偏移、字段宽度、调整值、剥离长度和整帧长度的关系。对应第 6、10 章。

4. **ByteBuf API**：<https://netty.io/4.1/api/io/netty/buffer/ByteBuf.html>。用于核对读写下标、派生缓冲区、copy 与 retained 方法。对应第 7 章。

5. **Reference counted objects**：<https://netty.io/wiki/reference-counted-objects.html>。用于核对引用计数和消息所有权。建议在开始直接操作 ByteBuf 前完整阅读。

6. **IdleStateHandler API**：<https://netty.io/4.1/api/io/netty/handler/timeout/IdleStateHandler.html>。用于核对空闲事件、时间配置和处理方式。对应第 9 章。

7. **Maven Central Netty 版本元数据**：<https://repo.maven.apache.org/maven2/io/netty/netty-all/maven-metadata.xml>。用于检查实际发布版本。取得的元数据显示 4.1.139.Final 与 4.2.19.Final；它不是安全更新公告，生产项目升级前仍需查看发布说明并回归验证。

8. **Netty 官方源码与示例**：<https://github.com/netty/netty>。继续阅读时优先选择与依赖版本相同的标签，例如 `netty-4.1.139.Final`，再查找 example 模块。

9. **Netty 安全公告**：<https://github.com/netty/netty/security/advisories>。用于后续维护时跟踪受影响版本与修复版本。固定版本有助于复现，不代表可以长期忽略安全更新。

阅读源码建议从你已经用过的类出发：先看 LineBasedFrameDecoder，再看 ByteToMessageDecoder，最后研究 EventLoop 与传输实现。带着具体问题读，例如“没有换行时字节存在哪里”，通常比从第一行一路读到底更有效。
