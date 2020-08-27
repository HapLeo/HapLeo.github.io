# Dubbo 学习笔记

## 引言

​	为什么要使用分布式架构？分布式架构解决了互联网应用的两大难题：高并发和高可用。高并发指服务器并发处理客户端请求的数量大，而高可用指后端服务能始终处于可用状态。

​	关于高并发，单机所能提供的并发量总是有限的。其受限于网络带宽、单机内存、CPU等。举个例子，假如单机需要10000并发请求，每次请求100KB,则需要10000x0.1MB=1000MB的流量，也就是1000MBx8=8000Mb的带宽（Byte与byte的转换）；如果使用Tomcat容器的默认Socket阻塞通信，每个请求启动一个处理线程，每个线程占用1M，则需要10000MB=10G内存。这会带来高昂的服务器成本。过多的线程会导致CPU的线程切换成本变大，以及JVM的GC能力负担等。而分布式架构可以将请求压力分散到集群的多台机器上。



> 注释：Web容器通常都需要配置最大并发请求数，Tomcat默认为150，也就是Tomcat中用来转发请求的线程池大小。



​	关于高可用，单体应用仅部署在单机上，一旦单点故障，客户端将无法访问服务。为了解决单点问题，引入分布式架构。分布式架构中，同一服务会部署在多台服务器上，一旦一台服务器宕机，其他服务器可以提供相同的服务。



​	Dubbo就是众多分布式框架中优秀的一种。Dubbo自己的定义如下：Apache Dubbo is a high-performance，java based，open source RPC framework. Dubbo是一个高性能的、基于Java的、开源的RPC框架。



​	高性能体现在多个方面，比如基于Netty的异步非阻塞IO，还比如基于TCP协议封装的简洁的dubbo协议。那么，什么是RPC框架呢？



​	在Spring经典的三层架构中，一个service调用另一个service的方法通常如下：

![spring单体方法调用](../images/spring单体方法调用.png)



订单服务的实现类中调用用户接口的方法，同时用户接口拥有一个实现类。在Spring环境下运行时，会自动生成用户类的代理实现类，以处理事务、日志等功能。此时，所有的接口和类都存在于同一个JVM中，通过JVM指令来调用方法。

![rpc远程方法调用](../images/rpc远程方法调用.png)



RPC(Remote Procedure Call):远程过程调用，也叫远程方法调用。指一个程序通过网络调用远程程序的方法的过程。分布式环境中，由于程序方法并不在同一JVM种，只能通过网络进行调用。两个服务均依赖同一套接口，而RPC框架则封装了网络请求部分，通过动态代理进行调用，使程序员在调用远程方法时能够像调用本地方法一样顺滑。



Dubbo使用什么网络协议进行远程方法的调用？Dubbo默认的协议为自定义的dubbo协议，同时也支持HTTP、Hessain、gRPC、webService、rmi、redis、rest、thrift、memcached等众多协议。需要用户根据不同的场景选用合适的协议。

- dubbo协议：采用单一长连接和NIO异步通讯，适合于**小数据量** **大并发**的服务调用，以及服务消费者数量远大于服务提供者数量的情况。文件上传等大数据量操作不适合此协议，建议数据量小于100K。数据通过hessian进行二进制序列化。

HTTP协议，HTTP(HyperText Transfer Protocol)：超文本传输协议。它是一种严格规范格式的应用层协议，基于TCP协议，传输的内容是固定格式的文本数据。



RPC框架通常封装了TCP协议进行二进制数据的传输，当然也可以封装HTTP协议来传输文本数据。TCP协议位于OSI模型的第四层，格式简单，传输效率更高。而HTTP协议位于OSI模型的第5、6、7层，封装了更多的格式数据，且文本格式较为松散，传输效率比二进制内容低，因此通常自定义TCP协议格式作为RPC框架的传输协议。当然 HTTP协议也具有一定的优势，比如在一些负载不高的应用中HTTP协议与TCP协议的传输差别并不是系统瓶颈，而HTTP协议能与Web端协议保持一致。而且HTTP/2协议已经对复杂的格式进行了优化，性能提升很多。



关于HTTP与RPC的关系问题，请参阅知乎问答：https://www.zhihu.com/question/41609070

小任务：基于动态代理实现一个简易的RPC框架。

## Dubbo源码模块划分

**dubbo-common:** 公共模块，存储工具类和公共逻辑，如SPI、时间轮、动态编译器等。

**dubbo-remoting:** 远程通信模块，提供远程调用功能。

**dubbo-rpc:** 远程调用协议模块，抽象各种协议，依赖dubbo-remoting模块的调用功能。

**dubbo-cluster:** 集群管理模块，提供负载均衡、容错、路由等功能，目的是将多个Provider伪装成一个Provider。

**dubbo-registry:** 服务注册模块，负责与注册中心交互。

**dubbo-monitor:** 监控模块，负责统计服务调用次数、调用时间及调用链路跟踪。

**dubbo-config:** 解析配置文件。

**dubbo-metadata:** 元数据模块。

**dubbo-configcenter:** 配置中心模块，负责外部配置和服务治理。

> 每个模块都提供了api模块及其实现模块，api模块提供了统一的实现规范，每个实现模块通过不同的方法实现功能。

****

## Dubbo的核心

### URL —— Dubbo的配置总线

**URL是什么？**

URL本质上是一个固定格式的字符串，这种格式是服务提供者与服务消费者之间公认的契约。Consumer通过传递URL来告知Provider它需要的资源。Provider通过解析URL来了解应该为Consumer提供什么资源。

**格式：**`protocol://username:password@host:port/path?key1=value1&key2=value2`

**username/password:** 用户名和密码，在HTTP Basic Authentication中经常使用该方式携带用户名和密码。

**url示例：**`dubbo://192.168.2.2:20880/org.apache.dubbo.demo.DemoService?application=dubbo-demo&dubbo=2.7.7&interface=org.apache.dubbo.demo.DemoService&methods=test&timestamp=158900014021`

**Dubbo中的URL定义**

```java
public final class URL implements Serializable {

    private final String protocol;

    private final String username;

    private final String password;

    // by default, host to registry
    private final String host;

    // by default, port to registry
    private final int port;

    private final String path;

    private final Map<String, String> parameters;
    
    ...
```

此外，dubbo-common包下提供了两个工具类：`URLBuilder`用于构造URL对象，`URLParser`用于将字符串URL解析成对象URL.

**应用场景**

- SPI中通过protocol确定RegistryFacotry的具体实现；



> Q: 讲一讲Dubbo中的URL? 
>
> K: 配置总线，格式，使用举例。
>
> A: 在Dubbo中，URL是贯穿整个框架核心的配置总线，它用来传递调用过程所需要的配置数据。它的格式为：`protocol://username:password@host:port/path?key1=value1&key2=value2`,url中包含了本次调用的协议、接口、方法名、传值、时间戳等。应用方面，比如dubbo通过协议名称来决定通过SPI加载哪一个协议的实现。



### SPI —— Service Provider Interface

OCP原则：对扩展开放，对修改封闭。

dubbo采用 **微内核+插件架构**，面向功能拆分，可扩展架构。内核负责管理插件的生命周期，不会因为系统功能变化而变化。所有功能实现都通过插件提供。

**思考：**为什么框架需要使用SPI机制？

**举例说明：** JDK提供了java.sql.Driver接口，该接口为不同的数据库厂商提供了统一的连接规范。每个数据库厂商提供这个接口的实现就可以连接不同的数据库。现在，我要写一个工具类，来实现一些自动查询的功能。要实现查询功能，是需要Driver的实例对象的，那么我应该如何获取Driver接口的实例对象呢？答案是由各个厂商的驱动包来提供。那么，当我引入myql的驱动包`jdbc-mysql-connector-*.jar`后，如何才能让我的工具类获取到mysql的Driver实例呢？在mysql的驱动包中，有一个文件夹`META-INF/services`,这个文件夹中保存了mysql厂商提供的Driver具体实现类的名称。我的工具要想使用到具体的Driver实例，只需要扫描这个文件夹，获取到相应的实现类名，就可以通过类加载器把该类加载进来并通过反射实例化对象出来。这样一来，不管是mysql还是Oracle，只要在它的驱动包里的这个目录下放入实现类名称，我的工具类就可以获取到它的实例对象并进行下一步调用了。这就是SPI的插件机制。

Dubbo没有完全采用JDK的SPI机制，而是对它做了增强。

在Dubbo中，RPC的过程可以通过dubbo协议，也可以通过http协议，他们分属于不同的实现包中，相当于两个插件。dubbo的核心api在发起调用的时候应该使用哪种协议呢？它会通过解析URL获得配置文件中的协议名称，然后通过Dubbo的SPI机制查找到具体的实现类并创建实例对象，从而实现RPC调用逻辑。因此，多个协议实现包并不会引起冲突。

**因此，SPI是一种通知机制，用于下层实现通知上层接口它的实现类的位置。**

> Q: 什么是SPI机制，dubbo是如何实现SPI的？
>
> K: 插件机制，JDK中的ServiceLoader,Dubbo中的
>
> A：SPI是一种插件机制，将接口与实现解耦，从而可以动态更改功能的实现。JDK的SPI通过在实现类的包中`META-INF\services`目录下存放文件，通用接口通过ServiceLoader加载指定的类来获取的实例。Dubbo对SPI机制进行了增强，通过关键字在运行时读取`META-INF\dubbo`目录下文件来加载指定的实现类。Dubbo中在一次调用中使用哪种协议、哪种负载均衡策略等都是通过Dubbo的SPI机制加载。





























