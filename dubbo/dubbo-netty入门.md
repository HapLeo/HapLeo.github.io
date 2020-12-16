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
BIO模型是阻塞型I/O模型，服务器每收到一个Socket连接，服务器就需要创建一个线程来处理该连接。
因为从连接的流中读取数据是阻塞的，当没有数据时会一直等待数据的到来，因此必须要为每个Socket连接创建一个线程
来处理流中的数据。但是当并发量大时，会导致频繁的线程切换，降低程序性能。连接建立后，线程只能阻塞等待，浪费资源。
示例代码如下：

```java
// 服务器端
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.Executors;

public class BIOServer {

    public static void main(String[] args) throws IOException {

        int port = 8000;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("服务器启动...");
		// 循环获取新的连接
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                // 为每个socket创建一个线程来处理
                Executors.newFixedThreadPool(1).execute(() -> {
                    try {
                        String client = socket.getInetAddress() + ":" + socket.getPort();
                        System.out.println("新的客户已连接：" + client);

                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String msg = null;
                        // 这里从流中读取的操作是阻塞的，即NIO,只有读到了内容才继续执行，否则一直阻塞在这里
                        while ((msg = reader.readLine()) != null) {
                            System.out.println(client + ":" + msg);
                            if (Objects.equals(msg, "exit")) {
                        System.out.println("当前socket关闭...");
                                break;
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        // 关闭socket，代码省略
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}


// 客户端
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Objects;

public class BIOClient {

    public static void main(String[] args) throws IOException {

        String host = "localhost";
        int port = 8000;

        try (Socket socket = new Socket(host, port)) {
            System.out.println("客户端启动...");

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
			// 从控制台读取输入的内容
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));
            String msg = null;
            while ((msg = consoleInput.readLine()) != null) {
                // 写入Socket流
                writer.println(msg);
                if (Objects.equals(msg, "exit")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```


### NIO模型
多个连接共用一个Selector对象，由Selector感知连接的读写事件。后台只需要很少的线程定期从Selector上查询连接的读写状态即可，无需大量线程阻塞等待。

示例：

```java
/**
 * 使用Netty创建服务端
 */
public class NettyServer {


    public static void main(String[] args) {

        int port = 8000;

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup boss = new NioEventLoopGroup();
        NioEventLoopGroup worker = new NioEventLoopGroup();
        serverBootstrap.group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .handler(new ChannelInitializer<NioServerSocketChannel>() {
                    @Override
                    protected void initChannel(NioServerSocketChannel ch) throws Exception {
                        System.out.println("服务端初始化中...");
                    }
                }).childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new FirstServerHandler());
                    }
                });
        serverBootstrap.attr(AttributeKey.newInstance("application.name"), "homebox-dubbo-customer-support-service");
        serverBootstrap.childAttr(AttributeKey.newInstance("client"), "clientAttrTest");
        serverBootstrap.bind(port).addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                if (future.isSuccess()) {
                    System.out.println("服务端启动成功...");
                } else {
                    System.out.println("服务端启动失败...");
                }
            }
        });

    }

}

/**
 * 使用Netty创建服务端
 */
public class NettyClient {


    public static void main(String[] args) {

        String host = "localhost";
        int port = 8000;

        Bootstrap bootstrap = new Bootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        System.out.println("客户端初始化中...");
                        ch.pipeline().addLast(new FirstClientHandler());
                    }
                });
        bootstrap.connect(host, port).addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                if (future.isSuccess()) {
                    System.out.println("连接成功！");
                } else {
                    System.out.println("连接失败！");
                }
            }
        });
        bootstrap.attr(AttributeKey.newInstance("clientAttr"), "clientAttrValue");
    }

}

public class FirstServerHandler extends ChannelInboundHandlerAdapter {

    /**
     * 接收到客户端的消息时触发
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        System.out.println("消息接收：" + byteBuf.toString(Charset.forName("utf-8")));
    }
}

public class FirstClientHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        System.out.println("channel active(客户端连接成功) ...");
        ByteBuf buffer = ctx.alloc().buffer();
        String message = "hello,I am clientA.";
        byte[] bytes = message.getBytes(Charset.forName("utf-8"));
        buffer.writeBytes(bytes);
        ctx.channel().writeAndFlush(buffer);
        System.out.println("消息发送：" + message);
    }
}
```

### Netty核心API

#### 服务端

**ServerBootstrap**: 服务端引导类，通过该引导类指定boss线程组和worker线程组、线程模型、处理流程、启动服务等。

	- group(EventLoopGroup parentGroup, EventLoopGroup childGroup) : 指定boss线程组和worker线程组；
	- channel(Class<? extends C> channelClass) : 指定线程模型，`NioServerSocketChannel.class`为NIO模型，`OioServerSocketChannel.class`为BIO线程模型。
	- handler(ChannelHandler handler) : 指定处理流程，即业务逻辑。
	- bind(int port) : 指定服务端口号。

#### 客户端

**Bootstrap:** 客户端引导类，可以指定线程组、线程模型、处理流程、连接服务等。

- group(EventLoopGroup group) : 指定线程组。
- channel(Class<? extends C> channelClass) : 指定线程模型，`NioServerSocketChannel.class`为NIO模型，`OioServerSocketChannel.class`为BIO线程模型。
- handler(ChannelHandler handler) : 指定处理流程，即业务逻辑。
- connect(String host,int port) : 指定连接的服务器地址和端口号。

#### ByteBuf

ByteBuf是数据传输的载体，发送和接收消息都需要ByteBuf作为载体进行传递。

ByteBuf通过两个指针readerIndex、writerIndex来控制可读空间和可写空间。



### 自定义协议

无论是使用 Netty 还是原始的 Socket 编程，基于 TCP 通信的数据包格式均为二进制，按照解析二进制的格式就是通信协议。

通信协议常用的基本格式如下，可以根据实际情况进行调整：

| 魔数  | 版本号 | 序列化算法 | 指令  | 内容长度 | 内容  |
| ----- | ------ | ---------- | ----- | -------- | ----- |
| 4字节 | 1字节  | 1字节      | 1字节 | 4字节    | N字节 |