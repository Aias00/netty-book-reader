# Netty 电子书交付记录

本轮将既有《从零开始学 Netty》整理为在线阅读与 EPUB，保持 15 章正文、4 个附录与原有 Java Maven 示例。验证日期为 2026-10-21。

## 维护与公开边界

现有 `Aias00/netty-book` 保持私有，不公开其历史。公开仓库拟为 `Aias00/netty-book-reader`，仅同步审核过的书稿、构建工具、阅读器资源、Maven 工程和必要验证记录，创建独立干净历史。官方资料缓存、旧运行日志、私有仓库历史和原稿生成器不进入公开快照。

## 已完成验证

- 通读前言、15 章与 4 个附录；保留教学示例不含 TLS、鉴权、完整生产停机、业务流控和 HTTP/WebSocket 工程的边界。
- 重新访问 7 个官方来源，均成功；Maven Central 元数据包含固定依赖 `4.1.139.Final`。证据：`research/source-revalidation.json`。
- JDK 21.0.12.1、Java release 17、Maven 3.10.0：5 项 JUnit 测试通过，覆盖拆分/合并、中文字符跨字节边界、退出关闭、超长行、长度字段分片。
- Java 客户端与真实 TCP Socket 实验通过；输出含中文、连续消息与退出应答。证据：`research/network-verification.json`。
- 在线正文由同一 `manuscript/book.md` 静态生成，前言加 15 章、4 附录共 20 个阅读单元。源码、EPUB、网页正文、搜索索引、目录和实验包的一致性检查通过。

- EPUBCheck 5.3.0：0 错误、0 警告；内部 XML、目录、源码覆盖检查通过。
- Chromium：浅色默认、深色跨标签同步、目录、搜索、字号、存储不可用回退均通过；20 个阅读单元在 320/390px 无页面横向溢出，禁用 JavaScript 可阅读和翻页。已目视检查浅色桌面与 320px 深色截图。证据：`research/reader-validation.json`。

## 已发布

- 在线阅读：<https://aias00.github.io/netty-book-reader/>
- 公开源码：<https://github.com/Aias00/netty-book-reader>，产品源码提交 `a6fd0ce6a1c985c8b6e6a76003d9a54c9dd5a822`。
- 首次发布：<https://github.com/Aias00/netty-book-reader/releases/tag/v1.0.0>
- EPUB：<https://github.com/Aias00/netty-book-reader/releases/download/v1.0.0/netty-book-reader.epub>
- Actions：<https://github.com/Aias00/netty-book-reader/actions/runs/39568186174>，Java/TCP、构建一致性、EPUBCheck、浏览器和 Pages 部署全部成功。
- 在线站再次用真实 Chromium 完整复测通过；20 个阅读单元在 320/390px 均无页面横向溢出，目录、搜索、字号、共享主题、无 JavaScript 翻页和下载均通过。
- Release 直接使用上述 CI 产物；在线 EPUB、Release EPUB 与 CI 文件三者 SHA-256 相同：`5f1c824185cf9bb728b08c0cbb43429a517921ce8a0e116ccd5ef7513b29c68d`。线上实际下载后检查 EPUB mimetype 通过。
- 发布证据见 `research/publication-validation.json`。后续证据文档提交不改变本次已验收的产品源码和发布产物。原 `netty-book` 仍保持 private。


## 验证范围

没有真实公网业务压测、TLS/鉴权集成或长期稳定性证据。源码中的 5 项测试与 TCP 实验用于验证教学协议，不能据此声称生产吞吐或故障恢复保证。
