# Zookeeper简析

[TOC]

**zookeeper 是一种分布式协调服务中间件，用来解决分布式一致性问题，通常被用作注册中心、配置中心、分布式锁等场景。**



## 数据模型 - ZNode

zookeeper 提供了一种树状结构来存储数据，这种结构类似于文件系统的目录。树状结构的节点称为 ZNode。

**ZNode 有四种类型：临时节点、临时顺序节点、持久节点、持久顺序节点。**

**临时节点：**当客户端断开连接后，zookeeper 会自动删除该节点创建的所有临时节点。临时节点没有子节点。

**临时顺序节点：**创建此类节点时，zookeeper 自动在节点名称后面拼接一个递增的计数。

**持久节点：**客户端断开连接后不会自动删除的节点。

**持久顺序节点：**与临时顺序节点类似，不同的是客户端断开连接后不会自动删除此类节点。

zookeeper客户端可以对ZNode进行增删改查和**监听**操作。
当一个 ZNode 被客户端监听后，一旦这个 ZNode 发生了变化，zookeeper 就会通知所有监听该 ZNode 的客户端。
这里所说的 ZNode 发生变化，包括新增、删除、修改当前的 ZNode。也就是说，可以监听一个不存在的ZNode。
需要注意的是，**Watcher 是一次性的**，如果需要继续监听则需要重新注册。



## 集群角色

在 zookeeper 集群中，一个 zookeeper 实例称作一个 zookeeper 节点。zookeeper 集群采用**一主多从**的架构。

**zookeeper 节点有三种角色：Leader、Follower 和 Observer，他们分工明确。**

**Leader 节点：**在集群中是唯一的存在，负责处理读请求和全部的写请求，并将修改的信息同步到其他节点。当然，也可以从 Leader 读取数据。

**Follower 节点：**负责集群中的读请求和 Leader 的选举。

**Observer 节点：**只负责读请求，不参与 Leader 的选举。

Observer 节点的作用是提高 zookeeper 集群读操作的吞吐量。因为 Leader 处理写请求时需要同步半数以上的 Follower 节点，因此，通过增加 Follower 节点的数量来提高吞吐量会影响写请求的效率，引入 Observer 节点则避免了这一问题。

当一个客户端实例与 Follower 或 Observer 建立长连接后，如果发送了写操作的请求，则该 zookeeper 节点会将写操作请求转发给 Leader 来执行。

## ZAB 协议

**ZAB 协议(Zookeeper Atomic Broadcast)**，译为**Zookeeper 原子广播协议**。ZAB 协议有两种模式：消息广播和崩溃恢复。

在消息广播和崩溃恢复的过程中，**zxid** 起到了关键的作用。

zxid 为全局唯一递增的事务 ID**，**是一个 64 位的值，高 32 位称为 epoch（纪元），低 32 位为自增计数器。每次Leader收到写请求，就会将zxid的低32位递增，并将新的zxid保存到proposal中。每次重新选举后新的Leader将 高32位递增，低32位归零。

### 消息广播 

在 zookeeper 中，Leader 节点负责整个集群的写请求和数据同步，由于该过程需要将写请求发送到每个 Follower 节点，因此被称作消息广播。

消息广播大致分为**四步：**


* **Leader 发送 proposal：**Leader 节点接收到写请求后，为该请求分配一个**zxid**，用于保证写操作的顺序性。Leader 节点内部为每一个 Follower 节点维护一个 FIFO 队列，当有写请求时，Leader 将请求包装成提案(**proposal**) 放入其内部维护的每个 Follower 对应的队列中，这种方式保证向 Follower 发送消息的顺序性。
* **Follower 返回 ACK：**Follower 节点收到提案后，写入本地事务日志，并给 Leader 返回 ACK 响应。
* **Leader 发送 COMMIT：**当 Leader 收到过半节点的 ACK 响应后，就向所有 Follower 发送**COMMIT**指令，并提交本地事务。
* **Follower 执行 COMMIT：**Follower 收到**COMMIT**指令后，提交本地事务，写操作结束。
### 崩溃恢复(选主)

zookeeper 的崩溃恢复指的是 Leader 宕机之后的选举过程。

选举的原则是，新Leader必须能保证：


1. 原 Leader 已经 COMMIT 的 proposal 必须让所有 Follower 进行 COMMIT；
2. 原 Leader 没有 COMMIT 的 proposal 必须让所有 Follower 进行删除；

具体的，当 Leader 宕机后，集群会经历两轮投票。


* **第一轮投票（投自己）：**

所有节点进入 Looking 状态，每个节点将自己的节点 id 和已经 COMMIT 的最大 zxid 组成键值对，将此键值对发送到集群中其他节点。

例如，在一个集群中有5个节点，id分别为：1、2、3、4 、5，它们已经commit的proposal的最大zxid分别为11、12、13、14、15，
那么第一轮投票时每个节点向其他节点发送的投票键值对分别为：（1,11）、 （2,12） 、（3,13）、（4,14）、（5，15）。

* **第二轮投票（投 zxid 最大者）：**

通过第一轮投票，每个节点都知道了其他节点的 zxid，因此从这些投票中选出 zxid 最大的键值对再次发送到其他节点。

上述例子中，在第二轮投票时，每个节点都发现id为5的节点的zxid最大，因此都给它投票，即每个节点都向其他节点发送（5,15）键值对。

当超过半数节点投了同一个节点时，该节点就成了新的 Leader。

新的 Leader 会将 epoch 加 1 发送给每个 Follower,Follower 收到新的 epoch 后返回 ACK 并带上自身最大的 zxid 和事务日志。

Leader 收到各 Follower 的最大 zxid 和事务日志后，开始为每个 Follower 维护一个 FIFO 队列，进行历史事务日志的同步。当过半的 Follower 同步成功后，Leader 才开始接收新的请求。



## 应用场景

### Master选举

在某些分布式场景中，某个业务只需要从多个服务实例中选出一个来执行，这个时候可以使用zookeeper的master选举功能实现。
使用zookeeper进行master选举的核心原理是多个服务实例同时向zookeeper的某个节点下新增一个相同的节点，由于zookeeper只允许一个新增成功，因此新增成功的那个节点就可以被认定为master节点。
Curater中`LeaderSelector`类实现了这一功能，可以通过该类方便的实现此业务。

### 分布式锁

在分布式系统中，由于通常会部署多个相同的服务实例，在执行相同的业务逻辑时需要竞争同一个资源，这时就需要一个分布式锁来协调资源的分配问题。比如高并发下的减库存操作。

- 排它锁
    zookeeper实现分布式排它锁的原理很简单，利用zookeeper的临时节点的特性，多个服务的线程在某个ZNode下同时创建一个同名的临时节点，创建成功的那个就是加锁成功的线程。
    加锁失败的线程则对该临时节点创建监听，一旦删除就重新竞争锁。
    Curater中`InterProcessLock`接口定义了了这一功能，并提供了`InterProcessMutex`这一实现。
    
- 共享锁
    共享锁指的是读操作之间共享，读写、写写操作排他的锁。zookeeper通过临时有序节点可以实现共享锁。
    多个服务的线程同时在某个ZNode下创建临时顺序节点，并在节点名称中携带读或写标志，例如：service-a 尝试获取写锁，于是在节点lock下创建子节点service-a-write，
    由于创建的是临时顺序节点，zookeeper会自动添加递增的后缀，名称变成service-a-write-0000000001;同理，service-b尝试获取读锁，新增的节点名称为service-b-read-0000000002;
    节点创建完毕后，每个服务都进行如下操作：
    获取lock的子节点列表，查看比自己小的节点列表中是否存在写操作，存在则继续监听lock节点的子节点，不存在则说明已经获取了锁，开始执行业务，执行完毕后删除自己的节点。
    
    Curator中的 `InterProcessReadWriteLock` 实现了这个逻辑。
    
如果一个服务实例注册了锁之后宕机了该怎么办？没关系，这个实例一旦与zookeeper断开连接，它新增的临时节点就会被zookeeper清理，而监听该节点的服务将收到通知。

### 分布式计数器

分布式计数器通常用于统计系统的在线人数，其实现原理是基于分布式锁来修改同一个节点的数据。Curator中`DistributedAtomicInteger`类实现了该功能。

