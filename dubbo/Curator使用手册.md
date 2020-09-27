# Curator 使用手册

[TOC]

**Curator是一个zookeeper开源客户端，用来方便的操作zookeeper服务器。**



### 启动Curator客户端

```java
// 指定服务端地址和端口
String zkAddress = "localhost:2181";
// 创建重试策略：指数级重试策略，初始重试时间为1000ms，重试三次（也就是说重试间隔为1000ms,2000ms,4000ms）
RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
// 创建客户端
CuratorFramework client = CuratorFrameworkFactory.newClient(zkAddress, retryPolicy);
// 启动
client.start();
```

客户端启动成功后就可以操作zookeeper服务器了。

### 新增节点

```java
String path = "/abc";
// 在path下创建一个持久节点
client.create().withMode(CreateMode.PERSISTENT).forPath(path);
// 在path下创建一个临时节点
client.create().withMode(CreateMode.EPHEMERAL).forPath(path);
// 在path下创建一个持久顺序节点
client.create().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath(path);
// 在path下创建一个临时顺序节点
client.create().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(path);
// 在path下创建一个持久定时节点
client.create().withMode(CreateMode.PERSISTENT_WITH_TTL).forPath(path);
// 在path下创建一个持久顺序定时节点
client.create().withMode(CreateMode.PERSISTENT_SEQUENTIAL_WITH_TTL).forPath(path);
```



### 删除节点

```java
client.delete().forPath(path);
```

### 修改节点

```java
// 修改节点就是修改节点保存的值，zookeeper保存的值为二进制数组，它不负责序列化
client.setData().forPath(path, value.getBytes());
```

### 查询节点

```java
byte[] bytes = client.getData().forPath(path);
return new String(bytes);
```

### 查询子节点

```java
client.getChildren().forPath(path);
```



## Watcher - 单次监听

### 监听节点变化- 增删改

当指定节点新增、修改、删除时触发：

```java
client.checkExists().usingWatcher((CuratorWatcher) watchedEvent ->
        System.out.println(watchedEvent.getPath() + watchedEvent.getState() + watchedEvent.getType()))
        .forPath(path);
```

### 监听节点变化-删改

当指定节点修改、删除时触发：

```java
client.getData().usingWatcher((CuratorWatcher) watchedEvent ->
         System.out.println(watchedEvent.getPath() + watchedEvent.getState() + watchedEvent.getType()))
         .forPath(path);
```

### 监听子节点变化- 新增、删除

当指定节点的子节点新增、删除时触发：

```java
client.getChildren().usingWatcher((CuratorWatcher) watchedEvent ->
        System.out.println("getChildrenWatch=>Path:"+ watchedEvent.getPath() +",state:"+ watchedEvent.getState() + ",type:"+watchedEvent.getType()))
        .forPath(path);
```



## Listener - 持续监听

### NodeCuratorListener

该类可以**监听指定节点和子节点的正删改操作**，但无法识别增删改类型。当注册监听器时，Curator自动为指定的节点和它的所有子节点（包括子节点的子节点等等）递归注册同样的监听。

```java
CuratorCache curatorCache curatorCache = CuratorCache.builder(client, path).build();
CuratorCacheListener listener = CuratorCacheListener.builder().forNodeCache(new NodeCacheListener() {
    @Override
    public void nodeChanged() throws Exception {
        System.out.println("nodeListener 监听到节点 " + path + "已变化。");
    }
}).build();
curatorCache.listenable().addListener(listener);
curatorCache.start();
// 移除这个监听器
curatorCache.listenable().removeListener(listener);
// 结束的时候close，归还资源
 curatorCache.close();
```





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


