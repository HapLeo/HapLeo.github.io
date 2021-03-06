## Dubbo-服务引入

> 注：本篇文章分析的Dubbo版本为v2.7.3，其他版本总体逻辑基本一致，细微之处不进行分析。

### 1. 简介 

**服务引入** 是指服务消费者获取服务提供者的服务实例的过程。

在本地调用时，例如在OrderServiceImpl中注入了IUserService接口的实现，可以看做是订单服务引入了用户服务。

在RPC框架中，由于服务之间跨进程，只能通过网络进行通信，因此订单服务引入用户服务时，引入的实际上是代理工厂创建的一个代理对象，而这个代理对象具有与用户服务远程通信的功能。

**服务引入的核心是：创建服务接口的代理对象。**

**服务引入总共分为三步：组装URL、生成Invoker和创建代理对象。**

- **组装URL：** 在Dubbo中，URL是配置总线，保存了所有需要的配置信息，并作为参数传递。 因此，服务引入的第一步便是收集配置信息并组装成URL。
- **生成Invoker:** invoker是一个执行器实例，服务消费者的Invoker实例负责**执行远程调用**，由Protocol的实现类通过URL构建得到。这里的Protocol实现类则通过`自适应扩展机制`获取。如果有多个注册中心，多个服务提供者，这个时候会得到一组 Invoker 实例，此时需要通过集群管理类 Cluster 将多个 Invoker 合并成一个实例。
- **创建代理对象：**通过`ProxyFactory.getProxy(invoker)`方法返回服务接口的代理实现对象。这一步是对invoker的封装，将Invoker伪装成Provider的服务实现，进而注入到Consumer中等待调用。

> 需要注意的是，服务导出过程的invoker是由代理工厂创建的，而这里的服务引入的invoker则是通过Protocol获取的。

### 2. 源码分析

Dubbo的服务引入是从`ReferenceConfig.get()`开始的。无论使用Spring或者API进行初始化，最终都会通过调用`ReferenceConfig.get()`来执行服务引入。

如下是通过API方式初始化的过程：

```java
    public static void main(String[] args) throws InterruptedException {

        // 应用配置
        ApplicationConfig application = new ApplicationConfig();
        application.setName("user-consumer");

        // 注册中心配置
        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("zookeeper://localhost:2180");

        // 引用配置
        ReferenceConfig<IUserService> reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setRegistry(registry);
        reference.setInterface(IUserService.class);
        reference.setVersion("1.0.0");

        // 获取引用的服务
        // get方法是关键方法，它是获取服务实现的入口
        IUserService userService = reference.get();
    }
```

具体过程如下：

```java
// ReferenceConfig.java
public synchronized T get() {
    checkAndUpdateSubConfigs();
    if (destroyed) {
        throw new IllegalStateException("The invoker of ReferenceConfig(" + url + ") has already destroyed!");
    }
    if (ref == null) {
        init();
    }
    return ref;
}
```

在ReferenceConfig中，get方法被`synchonized`修饰，保证线程安全性。`ReferenceConfig.get()`方法是服务引入逻辑的入口，不管通过什么引入方式，最终都要调用这个方法。`ref`是ReferenceConfig的属性，该属性就是`main`方法中想要获取的服务实现类的对象（这里返回的其实是代理类的对象）。这里直接返回，是因为`init()`方法已经为该字段赋值。

`init()`方法主要做了服务引入前期的准备工作，以及调用核心的`createProxy(map)`方法并将返回值赋值给`ref`属性。进入`init()`方法，简化后的代码如下：

```java
// ReferenceConfig.java
private void init() {

    // 将需要的配置参数收集到这个map中,待后续转换成url
    Map<String, String> map = new HashMap<String, String>();
    map.put(SIDE_KEY, CONSUMER_SIDE);
    ...
    // 这里创建代理类，传入map返回服务接口的代理类的实例
    ref = createProxy(map);
}
```

由上面分析可知，`init()`方法做了两件事，一是收集配置信息到map中，二是调用`createProxy(map)`方法并将返回值赋值给`ReferenceConfig`的`ref`属性。

接下来要分析的`createProxy(map)`方法封装了上述 **服务引用** 过程的三个核心步骤：`组装url`、`创建Invoker`、`创建代理对象`。

进入`createProxy`方法，简化后的代码如下：

```java
// ReferenceConfig.java
private T createProxy(Map<String, String> map) {
    // 本地JVM引入(不进行远程通信)，则：1. 通过map构建URL 2.通过Protocol协议获取Invoker实例
   if (shouldJvmRefer(map)) {
      URL url = new URL(LOCAL_PROTOCOL, LOCALHOST_VALUE, 0, 	interfaceClass.getName()).addParameters(map);
      invoker = REF_PROTOCOL.refer(interfaceClass, url);
    }else{
      // 远程引入(需要远程通信)
         if (url != null && url.length() > 0) {
          // 这里检测当前对象的url属性是否为空，如果不为空，说明用户主动为url赋值了
          // 用户主动为url赋值，通常是想忽略注册中心而直接连接他给定的url地址来获取服务
         String[] us = SEMICOLON_SPLIT_PATTERN.split(url);
         if (us != null && us.length > 0) {
         	for (String u : us) {
         		URL url = URL.valueOf(u);
         		if (StringUtils.isEmpty(url.getPath())) {
         		url = url.setPath(interfaceName);
         		}
         		if (REGISTRY_PROTOCOL.equals(url.getProtocol())) {
         			urls.add(url.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(map)));
         		} else {
         			urls.add(ClusterUtils.mergeUrl(url, map));
         		}
      	 	}
     	}
   	}else{
          // 这里表示要从注册中心获取url,并通过Proxy获取Invoker实例，如果有多个url，则将获取的多个invoke实例放到一个集合中，再用Cluster结合Directory融合成一个Invoker，这个Invoker具备负载均衡和容错能力
         }
 	}
    // 最终调用代理工厂的getProxy方法，传入Invoker获取代理实例
    return (T) PROXY_FACTORY.getProxy(invoker);
}
```

可以看到，`createProxy`方法判断了不同情况下的操作逻辑，但每一种逻辑最终都执行了上述三个步骤。

判断逻辑是用来区分用户希望如何引用服务：

1. map中包含本地引用的信息，则创建一个新的URL对象并打上本地引用的协议头`injvm://`；
2. 检查`ReferenceConfig`对象的`url`属性，如果不为空，是用户主动指定了一个或多个url地址(多个地址用分号隔开)，说明用户不想从注册中心获取url列表，测试场景通常会使用这种方式。因此不再从注册中心获取url列表。将url(或列表)存入`ReferenceConfig`对象的`urls`属性中。
3. 如果url为空，则从注册中心获取服务提供者的url列表，同样将整理好的url放入urls集合中。
4. 如果urls集合内只有一个元素，通过Porotocol生成Invoker实例；否则，循环每一个url，通过Protocol生成Invokers实例列表，再使用CLUSTER将多个invoker实例融合成一个Invoker实例，这个实例具有集群的特性，即具有 **负载均衡** 和 **容错重试** 等集群功能。
5. 最后通过代理工厂`ProxyFactory.getProxy(invoker)`方法返回代理对象。

至此，服务引入的三部曲的梗概就分析完了。接下来我们跳出`ReferenceConfig.java`进入到每一步中查看更深层的逻辑。

### 3. 深入细节

由上面的分析可知，服务引入过程分为**三步**：

**- 组装URL对象**；

**- 创建invoker对象;**

**- 返回服务代理类；**

接下来我们逐步分析。

#### 3.1 组装URL对象

URL是Dubbo中的配置总线，保存着各种配置信息。

组装URL，就是从各个配置类中收集配置参数，收集不到的就指定默认参数,并将这些参数赋值给URL的属性。

文章开头通过API调用的过程，首先组装各个配置类。这里为了方便查看再次粘贴一下：

```java
    public static void main(String[] args) throws InterruptedException {

        // 应用配置
        ApplicationConfig application = new ApplicationConfig();
        application.setName("user-consumer");

        // 注册中心配置
        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("zookeeper://localhost:2180");

        // 引用配置
        ReferenceConfig<IUserService> reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setRegistry(registry);
        reference.setInterface(IUserService.class);
        reference.setVersion("1.0.0");

        // 获取引用的服务
        // get方法是关键方法，它是获取服务实现的入口
        IUserService userService = reference.get();
    }
```

而URL的组装过程就是从这些配置类中获取配置参数的。

那么URL类的结构是什么样子呢？下面展示URL类的几个核心属性：

```java
// URL.java
public /*final**/
class URL implements Serializable {
	
    // 协议
    private final String protocol;

    // 用户名
    private final String username;

    // 密码
    private final String password;

    // 主机地址
    private final String host;

    // 端口号
    private final int port;

    // 路径
    private final String path;

    // 参数列表
    private final Map<String, String> parameters;
}
```

转换成字符串的格式：`protocol://username:password@host:port/path?key1=value1&key2=value2`

其中`username`和`password`在特定场景下使用。一般的url示例如下：

`dubbo://192.168.2.2:20880/org.apache.dubbo.demo.DemoService?application=dubbo-demo&dubbo=2.7.7&interface=org.apache.dubbo.demo.DemoService&methods=test&timestamp=158900014021`

组装好url之后，协议对象就可以解析这个url从而创建invoker对象了。

#### 3.2 创建invoker对象

Invoker是执行器，用于执行业务逻辑，在服务引入过程中负责执行RPC调用。它由协议接口Protocol的实现类实例获取，url作为创建方法的入参。

如下是Protocol获取invoker的源码片段：

```java 
//ReferenceConfig.java
private T createProxy(Map<String, String> map) {
    if (shouldJvmRefer(map)) {
        URL url = new URL(LOCAL_PROTOCOL, LOCALHOST_VALUE, 0, interfaceClass.getName()).addParameters(map);
        invoker = REF_PROTOCOL.refer(interfaceClass, url);
    }
}
```

可以看到，invoker对象是`REF_PROTOCOL.refer(interfaceClass, url)`方法返回的，`REF_PROTOCOL`是`Protocol`接口的一个实现类的实例，至于是哪个实现类，是通过Dubbo的**`SPI`机制** 确定的。关于`SPI机制`将会在另一篇文章中详细介绍。在这里，我们只要记住，invoker是通过协议获取的。

协议接口的定义如下：

```java
// Protocol.java
@SPI("dubbo")
public interface Protocol {

    int getDefaultPort();

    // 服务导出
    @Adaptive
    <T> Exporter<T> export(Invoker<T> invoker) throws RpcException;

    // 服务引入
    @Adaptive
    <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;
	
    void destroy();
}
```

在Dubbo中，Protocol接口有多个具体实现，其中典型的有：`DubboProtocol`和`RegistryProtocol`。

`DubboProtocol`负责构建用于RPC调用的invoker，而`RegistryProtocol`负责构建用于与注册中心进行交互的invoker。

当通过直连方式连接远程服务时，会直接通过`DubboProtocol`的实例获取invoker，而配置了注册中心的服务消费者会先通过`RegistryProtocol`进行 **服务订阅** ，执行成功后转换URL协议头到`dubbo://`(取决于你配置了哪种协议，默认dubbo协议)，再通过`RegistryProtocol`再次调用`Protocol.refer()`进行第二次的SPI，最终会调用`DubboProtocol`来构建负责RPC调用的invoker。

下面我们分析`DubboProtocol`获取Invoker的源码：

```java
// AbstractProtocol.java
// refer方法中调用protocolBindingRefer()方法，而该方法由子类实现。
@Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        return new AsyncToSyncInvoker<>(protocolBindingRefer(type, url));
    }

    protected abstract <T> Invoker<T> protocolBindingRefer(Class<T> type, URL url) throws RpcException;
```

下面是`DubboProtocol.java`中该方法的实现：

```java
// DubboProtocol.java
@Override
public <T> Invoker<T> protocolBindingRefer(Class<T> serviceType, URL url) throws RpcException {
    optimizeSerialization(url);
    // create rpc invoker.
    DubboInvoker<T> invoker = new DubboInvoker<T>(serviceType, url, getClients(url), invokers);
    invokers.add(invoker);
    return invoker;
}
```

这里逻辑比较简单，仅仅是创建了一个DubboInvoker对象并返回。DubboInvoker持有了一个客户端，一旦发起了RPC调用，DubboInvoker就会通过持有的客户端对服务提供者进行网络请求。
这里构造方法的参数比较重要。`serviceType`就是想要引入的实例的接口名，`url`是携带各种配置信息的载体，`getClients()`方法则是根据`url`获取通信组件的客户端，Dubbo默认返回`Netty`的客户端。这里返回的是`ExchangeClient`数组，`ExchangeClient` 接口的实现类是具体的通信客户端的包装类。获取客户端的过程如下：

```java
// DubboProtocol.java
private ExchangeClient[] getClients(URL url) {
    // whether to share connection
    boolean useShareConnect = false;
    int connections = url.getParameter(CONNECTIONS_KEY, 0);
    List<ReferenceCountExchangeClient> shareClients = null;
    // if not configured, connection is shared, otherwise, one connection for one service
    if (connections == 0) {
        useShareConnect = true;
        /**
         * The xml configuration should have a higher priority than properties.
         */
        String shareConnectionsStr = url.getParameter(SHARE_CONNECTIONS_KEY, (String) null);
        connections = Integer.parseInt(StringUtils.isBlank(shareConnectionsStr) ? ConfigUtils.getProperty(SHARE_CONNECTIONS_KEY,
                DEFAULT_SHARE_CONNECTIONS) : shareConnectionsStr);
        shareClients = getSharedClient(url, connections);
    }
    ExchangeClient[] clients = new ExchangeClient[connections];
    for (int i = 0; i < clients.length; i++) {
        if (useShareConnect) {
            clients[i] = shareClients.get(i);
        } else {
            clients[i] = initClient(url);
        }
    }
    return clients;
}
```

根据url中是否配置了`connections`参数来判断是否共享连接，如果`connections==0`则共享连接，否则初始化指定数量的客户端并返回。

获取共享连接的过程：

```java
// DubboProtocol.java
private List<ReferenceCountExchangeClient> getSharedClient(URL url, int connectNum) {
    String key = url.getAddress();
    List<ReferenceCountExchangeClient> clients = referenceClientMap.get(key);
    if (checkClientCanUse(clients)) {
        batchClientRefIncr(clients);
        return clients;
    }
    locks.putIfAbsent(key, new Object());
    synchronized (locks.get(key)) {
        clients = referenceClientMap.get(key);
        // dubbo check
        if (checkClientCanUse(clients)) {
            batchClientRefIncr(clients);
            return clients;
        }
        // connectNum must be greater than or equal to 1
        connectNum = Math.max(connectNum, 1);
        // If the clients is empty, then the first initialization is
        if (CollectionUtils.isEmpty(clients)) {
            clients = buildReferenceCountExchangeClientList(url, connectNum);
            referenceClientMap.put(key, clients);
        } else {
            for (int i = 0; i < clients.size(); i++) {
                ReferenceCountExchangeClient referenceCountExchangeClient = clients.get(i);
                // If there is a client in the list that is no longer available, create a new one to replace him.
                if (referenceCountExchangeClient == null || referenceCountExchangeClient.isClosed()) {
                    clients.set(i, buildReferenceCountExchangeClient(url));
                    continue;
                }
                referenceCountExchangeClient.incrementAndGetCount();
            }
        }
        /**
         * I understand that the purpose of the remove operation here is to avoid the expired url key
         * always occupying this memory space.
         */
        locks.remove(key);
        return clients;
    }
}
```

`getSharedClient`的逻辑是从缓存中获取与当前请求的**IP+端口**相同的客户端列表，如果这个列表为空，则通过`initClient()`创建并放进这个缓存中，然后返回。这里的`ReferenceCountExchangeClient`就是带有引用计数功能的`ExchangeClient`，记录了这个客户端被引用的次数。

`buildReferenceCountExchangeClient()`这个方法内调用了`initClient(url)`方法，我们直接看这个方法是如何初始化一个客户端的。

```java
// DubboProtocol.java
private ExchangeClient initClient(URL url) {
    // client type setting.
    String str = url.getParameter(CLIENT_KEY, url.getParameter(SERVER_KEY, DEFAULT_REMOTING_CLIENT));
    url = url.addParameter(CODEC_KEY, DubboCodec.NAME);
    // enable heartbeat by default
    url = url.addParameterIfAbsent(HEARTBEAT_KEY, String.valueOf(DEFAULT_HEARTBEAT));
    // BIO is not allowed since it has severe performance issue.
    if (str != null && str.length() > 0 && !ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(str)) {
        throw new RpcException("Unsupported client type: " + str + "," +
                " supported client type is " + StringUtils.join(ExtensionLoader.getExtensionLoader(Transporter.class).getSupportedExtensions(), " "));
    }
    ExchangeClient client;
    try {
        // connection should be lazy
        if (url.getParameter(LAZY_CONNECT_KEY, false)) {
            client = new LazyConnectExchangeClient(url, requestHandler);
        } else {
            client = Exchangers.connect(url, requestHandler);
        }
    } catch (RemotingException e) {
        throw new RpcException("Fail to create remoting client for service(" + url + "): " + e.getMessage(), e);
    }
    return client;
}
```

可以看到，通过` Exchangers.connect(url, requestHandler)`方法获取`client`实例。

```java
// HeaderExchanger.java
@Override
public ExchangeClient connect(URL url, ExchangeHandler handler) throws RemotingException {
    return new HeaderExchangeClient(Transporters.connect(url, new DecodeHandler(new HeaderExchangeHandler(handler))), true);
}

// NettyTransporter.java
@Override
public Client connect(URL url, ChannelHandler listener) throws RemotingException {
    return new NettyClient(url, listener);
}
```

Dubbo通过SPI获取`HeaderExchanger`实例并调用`connect()`方法，该方法构造并返回一个`HeaderExchangeClient`实例，构造实例的过程中调用`Transporters.connect()`方法，而该方法通过SPI最终会调用通信框架的API创建通信客户端并返回。

之所以在`Exhange`和`Transporter`中绕圈圈，是为了通过`SPI`来灵活的适配不同的通信框架，最终的目的就是为了获取到需要的通信框架客户端。

由上面我们知道，`DubboProtocol`会创建一个具有远程调用功能的`invoker`实例，而`RegistryProtocol`的作用则是注册服务消费者、订阅providers、configurators、routers 等节点数据，并返回具有集群功能的Invoker。

```java
// RegistryProtocol.java
private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
    RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
    directory.setRegistry(registry);
    directory.setProtocol(protocol);
    // all attributes of REFER_KEY
    Map<String, String> parameters = new HashMap<String, String>(directory.getUrl().getParameters());
    URL subscribeUrl = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, type.getName(), parameters);
    if (!ANY_VALUE.equals(url.getServiceInterface()) && url.getParameter(REGISTER_KEY, true)) {
        // 注册服务消费者
        directory.setRegisteredConsumerUrl(getRegisteredConsumerUrl(subscribeUrl, url));
        registry.register(directory.getRegisteredConsumerUrl());
    }
    directory.buildRouterChain(subscribeUrl);
    // 订阅服务提供者、配置、路由节点
    directory.subscribe(subscribeUrl.addParameter(CATEGORY_KEY,
            PROVIDERS_CATEGORY + "," + CONFIGURATORS_CATEGORY + "," + ROUTERS_CATEGORY));
    Invoker invoker = cluster.join(directory);
    ProviderConsumerRegTable.registerConsumer(invoker, url, subscribeUrl, directory);
    // 返回Invoker
    return invoker;
}
```

#### 3.3 创建代理对象

代理对象是服务实现的代理，也就是说当发起RPC调用时，会调用代理类的方法。这里的代理类封装了Invoker实例，当调用服务的方法时，就会通过代理类调用Invoker的invoke方法,进行RPC的调用。dubbo中提供了两种代理类的生成方式：`javassistProxyFactory`和`jdkProxyFactory`,关于动态代理，将在 [动态代理](dubbo-动态代理.md) 这篇文章中介绍。

从Dubbo官网拷贝的生成的代理对象的样子：

```java
public class proxy0 implements org.apache.dubbo.demo.DemoService {

    public static java.lang.reflect.Method[] methods;

    private java.lang.reflect.InvocationHandler handler;

    public proxy0() {
    }

    public proxy0(java.lang.reflect.InvocationHandler arg0) {
        handler = $1;
    }

    public java.lang.String sayHello(java.lang.String arg0) {
        Object[] args = new Object[1];
        args[0] = ($w) $1;
        Object ret = handler.invoke(this, methods[0], args);
        return (java.lang.String) ret;
    }
}
```

















