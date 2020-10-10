# Netty入门

> Netty is *an asynchronous event-driven network application framework* 
>  for rapid development of maintainable high performance protocol servers & clients.

*Netty*是一款异步的、事件驱动的网络应用程序框架，用以快速开发高性能、高可靠性的网络服务器和客户端程序。

## Java NIO 的问题
- API复杂不友好。
- 开发量大。需要自己补齐很多可靠性方面的实现，例如网络波动导致的连接重连、半包读写等。
- JDK自身的bug。例如著名的Epoll Bug会导致Selector空轮询，CPU使用率100%。

## Netty的优点
- 基于Java NIO的封装，只依赖JDK。
- API简单易用，文档齐全。
- 支持阻塞和非阻塞I/O模型。
- 代码质量高，主流版本基本没有bug。

## 网络模型
### BIO模型
BIO模型是阻塞型I/O模型，服务器每收到一个请求，就创建一个线程来处理该请求。当并发量大时，会导致频繁的线程切换，降低程序性能。连接建立后，线程只能阻塞等待，浪费资源。

### NIO模型
多个连接共用一个Selector对象，由Selector感知连接的读写事件。后台只需要很少的线程定期从Selector上查询连接的读写状态即可，无需大量线程阻塞等待。

