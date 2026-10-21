# 从零开始学 Netty

面向 Java 与网络编程初学者的中文读本，15 章正文、4 个附录，围绕可运行的 TCP 行协议工程学习 Pipeline、ByteBuf、消息边界、连接生命周期与测试。

- [在线阅读](https://aias00.github.io/netty-book-reader/)
- [下载 EPUB](https://aias00.github.io/netty-book-reader/downloads/netty-book-reader.epub)
- [版本发布与离线文件](https://github.com/Aias00/netty-book-reader/releases)
- [公开书稿、示例与构建源码](https://github.com/Aias00/netty-book-reader)

正文默认浅色，支持低饱和深色，使用与 Aias 其他电子书相同的 `aias-reader-theme` 偏好。目录、全文搜索、字号与继续阅读适配手机；禁用 JavaScript 仍可阅读正文、查看目录和翻页。

## 运行示例

使用 JDK 17 或更高版本与 Maven 3.9+。本轮在 JDK 21、Maven 3.10.0 上验证，依赖固定为 Netty `4.1.139.Final`。进入 `examples`：

```sh
mvn test
mvn compile exec:java -Dexec.mainClass=book.LineServer
```

在另一个终端进入 `examples`，运行客户端：

```sh
mvn compile exec:java -Dexec.mainClass=book.LineClient
```

预期输出为“收到：你好，Netty”和“再见”。默认仅监听本机 8080，服务器按 Ctrl+C 停止。修改端口时，双方命令都加 `-Dexec.args=9090`。

真实 TCP 路径也可自动验证，在项目根目录执行：

```sh
mvn -B -ntp -f examples/pom.xml test dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
python3 tools/verify_network.py
```

## 书稿、阅读站与离线构建

`manuscript/book.md` 是唯一书稿来源，已内嵌完整工程代码；`examples/` 保存可运行 Maven 工程。阅读元信息位于 `tools/reader-metadata.json`，`web/` 保存阅读器与共用主题，`epub/` 保存离线排版。安装 Python 3.10+、Pandoc 3.12.1 后执行：

```sh
sh tools/build.sh
python3 tools/reader_verify.py
python3 tools/audit_epub.py
sh tools/epubcheck.sh
```

静态阅读站与下载文件生成到 `site/`；原有中文 EPUB 与配套 ZIP 路径保留在 `dist/`。`reader_verify.py` 核对网页、EPUB、书稿、搜索与实验包，`epubcheck.sh` 使用 Maven 取得 EPUBCheck 5.3.0，警告也会使检查失败。

浏览器回归需 Node.js 22、Playwright 1.64.1 及 Chromium。在一个终端启动 `python3 -m http.server 8796 --bind 127.0.0.1 --directory site`，另一个终端执行 `BOOK_URL=http://127.0.0.1:8796/ node tools/browser-check.cjs`。

## 发布与验证范围

私人维护稿保留在原 `netty-book` 私有仓库。公开的 `netty-book-reader` 采用独立历史，仅同步审核过的书稿、构建源码与实验；不包含私有历史、官方文档缓存、旧日志或原稿生成器。维护仓库的 `tools/export_public.py` 生成 `.publication/` 白名单快照；公开仓库的 `.github/workflows/pages.yml` 负责测试、构建、EPUBCheck、手机回归及 Pages 发布。EPUB Release 按新版本创建，历史版本不覆盖。

本轮通过 5 项 JUnit 测试、真实 TCP 中文/分片/合并/退出实验、EPUBCheck 及 320/390px 阅读检查。详细证据见 [DELIVERY.md](DELIVERY.md)。教学示例没有实现 TLS、鉴权、完整生产停机、心跳协议与业务流控；HTTP/WebSocket、自定义二进制业务协议与生产扩容属于讲解或练习。没有大规模压力测试或生产性能保证。
