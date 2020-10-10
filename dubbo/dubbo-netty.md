# Java NIO 的问题
- API复杂不友好。
- 开发量大。需要自己补齐很多可靠性方面的实现，例如网络波动导致的连接重连、半包读写等。
- JDK自身的bug。例如著名的Epoll Bug会导致Selector空轮询，CPU使用率100%。

# Netty的优点
- 基于Java NIO的封装，只依赖JDK。
- API简单易用，文档齐全。
- 支持阻塞和非阻塞I/O模型。
- 代码质量高，主流版本基本没有bug。

# 网络模型
## BIO模型
BIO模型是阻塞型I/O模型，服务器每收到一个请求，就创建一个线程来处理该请求。
