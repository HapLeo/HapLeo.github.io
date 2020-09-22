# Zookeeper简析

[TOC]

** zookeeper 是一种分布式协调服务中间件，它通常被用作注册中心、配置中心、分布式锁等场景。**

## ZNode

zookeeper 提供了一种树状结构来存储数据，这种结构类似于文件系统的目录。树状结构的节点称为 ZNode。

**ZNode 有四种类型：临时节点、临时顺序节点、持久节点、持久顺序节点。**

**临时节点：当**客户端断开连接后，zookeeper 会自动删除该节点创建的所有临时节点。临时节点没有子节点。

**临时顺序节点：**创建此类节点时，zookeeper 自动在节点名称后面拼接一个递增的计数。

**持久节点：**客户端断开连接后不会自动删除的节点。

**持久顺序节点：**与临时顺序节点类似，不同的是客户端断开连接后不会自动删除此类节点。

## Watcher 监听

zookeeper 的 ZNode 除了具有保存数据的功能外，还具有监听功能。当一个 ZNode 被客户端监听后，一旦这个 ZNode 发生了变化，zookeeper 就会通知所有监听该 ZNode 的客户端。这里所说的 ZNode 发生变化，包括删除、修改当前 ZNode 和新增、删除、修改这个 ZNode 的子 ZNode。需要注意的是，Watcher 是一次性的，如果需要继续监听则需要重新注册。

## 客户端

zookeeper 通过客户端对 ZNode 节点进行增删改查操作。常用的 zookeeper 客户端有 zkClient 和 Curator。

每个客户端实例只能与一个 zookeeper 节点维持长连接。

## 集群角色

在 zookeeper 集群中，一个 zookeeper 实例称作一个 zookeeper 节点。zookeeper 集群采用**一主多从**的结构。

**zookeeper 节点有三种角色：Leader、Follower 和 Observer，他们分工明确。**

**Leader 节点：**在集群中是唯一的存在，负责处理读请求和全部的写请求，并将修改的信息同步到其他节点。当然，也可以从 Leader 读取数据。

**Follower 节点：**负责集群中的读请求和 Leader 的选举。

**Observer 节点：**只负责读请求，不参与 Leader 的选举。

Observer 节点的作用是提高 zookeeper 集群读操作的吞吐量。因为 Leader 处理写请求时需要同步半数以上的 Follower 节点，因此，通过增加 Follower 节点的数量来提高吞吐量会影响写请求的效率，引入 Observer 节点则避免了这一问题。

当一个客户端实例与 Follower 或 Observer 建立长连接后，如果发送了写操作的请求，则该 zookeeper 节点会将写操作请求转发给 Leader 来执行。

## ZAB 协议

**ZAB 协议(Zookeeper Atomic Broadcast)**，译为**Zookeeper 原子广播协议**。ZAB 协议分为两个部分：消息广播和崩溃恢复。

在讲消息广播和崩溃恢复之前，先讲一下 zxid。

**zxid 为全局唯一递增的事务 ID**，**是一个 64 位的值，高 32 位称为 epoch（纪元），低 32 位为自增计数器。每次重新选举后 epoch 加 1，自增计数器归零。**

### 消息广播

在 zookeeper 中，Leader 节点负责整个集群的写请求和数据同步，由于该过程需要将写请求发送到每个 Follower 节点，因此被称作消息广播。

消息广播大致分为**四步：**


* **Leader 发送 proposal：**Leader 节点接收到写请求后，为该请求分配一个**zxid**，用于保证写操作的顺序性。Leader 节点内部为每一个 Follower 节点维护一个 FIFO 队列，当有写请求时，Leader 将请求包装成提案(**proposal**) 放入其内部维护的每个 Follower 对应的队列中，这种方式保证向 Follower 发送消息的顺序性。
* **Follower 返回 ACK：**Follower 节点收到提案后，写入本地事务日志，并给 Leader 返回 ACK 响应。
* **Leader 发送 COMMIT：**当 Leader 收到过半节点的 ACK 响应后，就向所有 Follower 发送**COMMIT**指令，并提交本地事务。
* **Follower 执行 COMMIT：**Follower 收到**COMMIT**指令后，提交本地事务，写操作结束。
### 崩溃恢复(选主)

zookeeper 的崩溃恢复指的是 Leader 宕机之后的选举过程。

选举的原则是：


1. 原 Leader 已经 COMMIT 的 proposal 必须让所有 Follower 进行 COMMIT；
2. 原 Leader 没有 COMMIT 的 proposal 必须让所有 Follower 进行删除；

具体的，当 Leader 宕机后，集群会经历两轮投票。


* **第一轮投票（投自己）：**

所有节点进入 Looking 状态，每个节点将自己的节点 id 和已经 COMMIT 的最大 zxid 组成键值对，将此键值对发送到集群中其他节点。

例如，在一个集群中有5个节点，id分别为：1、2、3、4 、5，它们已经commit的proposal的最大zxid分别为11、12、13、14、15，那么第一轮投票时每个节点向其他节点发送的投票键值对分别为：（1,11）、 （2,12） 、（3,13）、（4,14）、（5，15）。

* **第二轮投票（投 zxid 最大者）：**

通过第一轮投票，每个节点都知道了其他节点的 zxid，因此从这些投票中选出 zxid 最大的键值对再次发送到其他节点。

上述例子中，在第二轮投票时，每个节点都发现id为5的节点的zxid最大，因此都给它投票，即每个节点都向其他节点发送（5,15）键值对。

当超过半数节点投了同一个节点时，该节点就成了新的 Leader。

新的 Leader 会将 epoch 加 1 发送给每个 Follower,Follower 收到新的 epoch 后返回 ACK 并带上自身最大的 zxid 和事务日志。

Leader 收到各 Follower 的最大 zxid 和事务日志后，开始为每个 Follower 维护一个 FIFO 队列，进行历史事务日志的同步。当过半的 Follower 同步成功后，Leader 才开始接收新的请求。





