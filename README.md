# [Dubbo](dubbo/Dubbo.md)

Dubbo是一款高性能Java RPC框架。

分布式架构提供了互联网应用必须的高并发、高可用特性，Dubbo作为RPC框架提供了方便的远程方法调用、负载均衡、集群容错等功能。

**协议：**Dubbo默认使用自定义的dubbo协议，基于[Netty](dubbo/dubbo-netty入门.md)的NIO异步通讯，适合小数据量、大并发的服务调用,数据通过hessian进行二进制序列化。

**[SPI](dubbo/dubbo-SPI.md)：** Dubbo采用插件化设计，通过SPI机制动态调整服务的实现。Dubbo的SPI对JDK的SPI进行了增强，包括通过key加载指定的服务实现，Wrapper自动包装、自动依赖注入以及Adaptive自适应加载机制。

**[服务导出](dubbo/dubbo-服务导出.md)：** 指服务提供者Provider开启网络服务并将URL注册到注册中心的过程。Dubbo默认的网络服务是Netty,默认的注册中心是[zookeeper](dubbo/dubbo-zookeeper.md)。服务导出的过程大致可以分为三个部分：组装URL、生成invoker和开启与注册服务。

**[服务引入](dubbo/dubbo-服务引入.md)：** 指服务消费者Consumer从注册中心获取服务提供者的网络接口并封装成本地服务代理的过程。Dubbo默认使用javassist来实现[动态代理](dubbo/dubbo-动态代理.md)负责生产服务代理实例,同时也支持JDK动态代理。



# MySQL

### 读书笔记

[MySQL知识树](mysql/MySQL知识树.md)

[MySQL基本配置](mysql/读书笔记/MySQL基本配置.md)

[MySQL存储结构](mysql/读书笔记/MySQL存储结构.md)

[InnoDB索引](mysql/读书笔记/InnoDB索引.md)

[InnoDB表空间](mysql/读书笔记/InnoDB表空间.md)

[optimizer_trace](mysql/读书笔记/optimizer_trace.md)

[Buffer Pool](mysql/读书笔记/Buffer Pool.md)

[Redo Log](mysql/读书笔记/Redo_Log.md)

### 重点总结

索引

锁

事务

# Java

- 集合
- 多线程
- JVM
  - [JVM常用参数表](jvm/JVM常用参数表.md)

## 数据结构

- 线性结构

- 树形结构

- 图论

## 算法
- 动态规划算法
- 分治法
- 贪心算法
- 回溯算法

## Database

- MySQL
- Redis
- MongoDB

## 架构

- DDD
- Microservice

## 框架
- Spring
- Spring Cloud
- Spring Boot 



## 随笔

[学习计划](随笔/学习计划.md)

[技术面试思考](随笔/技术面试思考.md)

[学习方向](随笔/学习方向.md)

[ubuntu装机指南](linux/ubuntu装机指南.md)

[布隆过滤器简介](redis/布隆过滤器简介.md)