### 简介

**kafka 是什么？**

**Apache Kafka is an open-source distributed event streaming platform.**

Kafka 是一个开源的分布式事件流平台。

**Tips:**

* 它是由 Java 和 Scala 编写的分布式消息中间件；
* 它是发布订阅模式，即 consumer 订阅一个或多个 topic；
* 它高度依赖 zookeeper，没有 zookeeper 就无法运行；
* 它安全的将流式的数据存储在一个分布式，有副本备份，容错的集群；
* 它使用生产者推、消费者拉的模式；
* 它存储的消息记录默认保存 7 天，通过参数`log.rentention.hours`可调节,到期自动删除。

**什么是事件流？**

事件流是指从数据库、传感器、移动设备等数据源实时捕获数据并对其进行存储、路由、处理的过程。

**事件流能做什么？**

* 实时处理付款和金融交易，例如在证券交易所，银行和保险中。
* 实时跟踪和监视汽车，卡车，车队和货运，例如在物流和汽车行业。
* 连续捕获和分析来自 IoT 设备或其他设备（例如工厂和风电场）中的传感器数据。
* ...

**Kafka 是一个事件流平台，这意味着什么？**

kafka 的三个关键能力：

* 发布&订阅
* 消息存储
* 消息处理
### 核心概念

* **broker（代理节点）：通常指一个服务器节点；**
* **topic（主题）：类似于文件系统中的文件夹，事件消息相当于文件存放在其中；**
* **partition（分区）：一个主题被拆分成一个或多个部分，分布在不同的服务器节点中；**
* **productor（生产者）: 生产事件消息的程序；**
* **consumer（消费者）：消费事件消息的程序；**
### 配置项

* `broker.id`集群中的节点 ID
* `log.dirs`日志记录存放目录(kafka 中每个消息被称作日志，这里存放的就是事件消息数据)
* `zookeeper.connect`zookeeper 的注册地址，例:`localhost:2181`

### 架构

topic：发布订阅模型中的主题，一个 consumer 可以订阅一个或多个 topic，topic 是逻辑概念，与物理机器无关。

partition：分区，为了做到高可用，kafka 把一个 topic 拆分成多个分区存储在各个物理机的硬盘中，在一个 partition 内的消息有序。为了防止单点问题，一个分区的数据可以复制到其他多台机器上，并选举一个机器作为这个分区的 Leader。

一个主题在多台服务器上的分布情况：

|topic|server1|server2|server3|
|:----|:----|:----|:----|
|topic1|partition1(Leader)|partition1|partition1|
|    |partition2|partition2(Leader)|partition2|
|    |partition3|partition3|partition3(Leader)|

注意：只有 Leader 负责读写，Fllower 只负责主动从 Leader 同步数据；

* 分区与消费者如何对应？

![图片](https://uploader.shimo.im/f/cHLAtwKNXdiyDXO3.png!thumbnail?fileGuid=HjCgT9VgDYVWVXtC)

消费者以组的名义订阅主题，主题有多个分区，消费者组中有多个消费者实例，最好的配置方案是分区数=消费者数。

分区数>消费者数？此时会出现一个消费者消费两个分区，无法保证消息有序性；

分区数<消费者数？此时会出现空闲的消费者，浪费服务器资源。

当然，也可以自定义分配方案。

### API

* 生产者客户端
* 消费者客户端
### 问答

1. 请说明 ISR 的作用？



Kafka与RabbitMQ的选型？

- 相同点
  - 都是分布式消息队列
  - 都支持消息持久化
  - 都有副本实现高可用
- 不同点
  - Kafka是发布订阅模式，通过订阅topic获取消息，使用单一消费组可实现点对点模式；RabbitMQ是点对点模式，通过Queue获取消息，使用Exchange路由来实现发布订阅模式；
  - 顺序性：Kafka保证了分区顺序性，RabbitMQ保证了队列顺序性；
  - 性能：Kafka的topic partition分布在多台机器上，支持多机并发，RabbitMQ的Queue并未分片，只能由一台broker接受consumer消费；
  - 容灾：Kafka对分区做冗余副本，RabbitMQ对Queue做冗余副本；
  - 消息持久化：Kafka中的消息消费完毕并不会立刻删除，而是设置过期时间自动删除，RabbitMQ中的消息消费完毕会被立刻删除。
  - RabbitMQ自带了监控页面，方便运维，Kafka没有；

总结：Kafka相较于RabbitMQ，将消息进行了分片处理，提高了吞吐量。因此，对吞吐量有需求的系统应当选用Kafka，在吞吐量没有要求的情况下两者皆可。


