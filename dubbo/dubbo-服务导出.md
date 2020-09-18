## Dubbo-服务提供者的初始化过程

服务提供者的初始化过程，从ServiceConfig.export() 开始。

ServiceConfig保存了服务提供者的协议信息、URL、服务接口名、服务类、服务方法、该服务是否已被暴露等。

export的含义有两个，一是开启网络接口服务以接收远程调用请求，二是将接口URL暴露到注册中心。

以下逐步分析这两个过程。

```java
// ServiceConfig.java
// 检查配置以及是否延迟暴露后，最终调用doExport()方法
public synchronized void export() {
        checkAndUpdateSubConfigs();

        if (!shouldExport()) {
            return;
        }

        if (shouldDelay()) {
            DELAY_EXPORT_EXECUTOR.schedule(this::doExport, getDelay(), TimeUnit.MILLISECONDS);
        } else {
            doExport();
        }
    }
```

在ServiceConfig类中,检测各种配置是否合规，是否可以暴露，是否延迟暴露。

```java
// ServiceConfig.java
// 仍然在ServiceConfig类中，这个方法时一个同步方法，保证同一个服务不会被多次暴露，之后调用doExportUrls()方法开始真正的暴露服务逻辑
protected synchronized void doExport() {
        if (unexported) {
            throw new IllegalStateException("The service " + interfaceClass.getName() + " has already unexported!");
        }
        if (exported) {
            return;
        }
        exported = true;

        if (StringUtils.isEmpty(path)) {
            path = interfaceName;
        }
        doExportUrls();
    }
```

```java
// ServiceConfig.java
// 仍然在ServiceConfig类中绕圈圈，
//这个方法实现多注册中心多协议暴露服务的逻辑
//首先获取了所有的注册中心地址，然后循环所有协议配置，通过doExportUrlsFor1Protocol(protocolConfig, registryURLs)方法将一个协议服务导出到多个注册中心。
	private void doExportUrls() {
        List<URL> registryURLs = loadRegistries(true);
        for (ProtocolConfig protocolConfig : protocols) {
            String pathKey = URL.buildKey(getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), group, version);
            ProviderModel providerModel = new ProviderModel(pathKey, ref, interfaceClass);
            ApplicationModel.initProviderModel(pathKey, providerModel);
            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
        }
    }
```

接下来看doExportUrlsFor1Protocol这个方法，该方法的核心逻辑是将服务的某一个协议URL暴露到所有注册中心。此方法源码非常复杂，这里就不贴源码了，只摘出核心逻辑进行分析。

```java
// 还是在ServiceConfig.java类中
// 第一步：将组织各种配置信息，包括ServiceConfig上面的属性信息、默认配置等，这些包含着协议、端口、服务名、方法名等信息的配置放入一个map中，最终转换成一个URL对象。
// 第二步：将URL对象、服务接口类等传入代理工厂，通过代理生成执行器Invoker对象，该对象是逻辑执行的核心
// 第三步：将执行器对象invoker传入协议对象protocol的export()方法，进行协议层的暴露
private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> 		registryURLs) {
        String name = protocolConfig.getName();
        if (StringUtils.isEmpty(name)) {
            name = DUBBO;
        }
    	// 用一个map来收集所有配置信息
        Map<String, String> map = new HashMap<String, String>();
        map.put(SIDE_KEY, PROVIDER_SIDE);
		// 这里也是将各种配置信息放入map中
        appendRuntimeParameters(map);
        appendParameters(map, metrics);
        appendParameters(map, application);
        appendParameters(map, module);
    	...
// 重要：最终将所有配置转换成一个URL对象
URL url = new URL(name, host, port, getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), map);
// 这个时候的url是这样(dubbo开头，参数register=true)：
//dubbo://192.168.31.100:20880/top.hapleow.service.IUserService?anyhost=true
//&application=hello-world-app&bean.name=top.hapleow.service.IUserService
//&bind.ip=192.168.31.100&bind.port=20880&deprecated=false&dubbo=2.0.2
//&dynamic=true&generic=false&interface=top.hapleow.service.IUserService
//&methods=getById,list&pid=5840&register=true&release=2.7.3&side=provider
//&timestamp=1599097048451 
    	...
String scope = url.getParameter(SCOPE_KEY);
// 如果scope没有被指定为remote，就先导出本地服务（injvm）
if (!SCOPE_REMOTE.equalsIgnoreCase(scope)) {
    			// exportLocal其实就是创建了一个InjvmExporter对象，说明这个服务在本地可以调用
                exportLocal(url);
}
 ...
// 这里判断是否在xml中指定了注册中心地址，如果指定了，则执行下面的逻辑：服务开启 + 服务注册
if (CollectionUtils.isNotEmpty(registryURLs)) {
        // 循环注册中心URL,准备将当前服务逐个注册到每一个注册中心
        for (URL registryURL : registryURLs) {
            // registryURL的开头是 registry
            // 把注册中心的URL、服务url和服务接口类提供给代理工厂，生成了一个Invoker对象
            // Invoker对象是执行器，也是各种执行逻辑的核心，这里getInvoker(T proxy, Class<T> type, URL url) 方法的三个参数中包含了URL，会将URL作为Invoker的属性保存起来
            Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, 				registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
            // 对Invoker进行一次包装
            DelegateProviderMetaDataInvoker wrapperInvoker = new 		DelegateProviderMetaDataInvoker(invoker, this);
            // 使用protocol协议进行暴露
Exporter<?> exporter = protocol.export(wrapperInvoker);
            exporters.add(exporter);
        }
} else {
    // 如果没有给定注册中心地址，则直接将url传入代理工厂生成invoker，这个url的协议头为默认的dubbo
	Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, url);
	DelegateProviderMetaDataInvoker wrapperInvoker = new 				DelegateProviderMetaDataInvoker(invoker, this);
	Exporter<?> exporter = protocol.export(wrapperInvoker);
	exporters.add(exporter);
}
```

我们知道，暴露服务的本质就是**开启网络服务**和**写入注册中心**，这两步都需要底层协议的支持，其逻辑都包含在`protocol.export()` 方法中。那么这里的`protocol`对象是哪来的呢？

```java
// ServiceConfig.java
// 其实这个protocol是ServiceConfig的一个类属性，通过Dubbo的SPI在类的初始化阶段就赋值了
// 但要注意，这里返回的Protocol对象是被ProtocolFilterWrapper、ProtocolListenerWrapper和QosProtocolWrapper三层包裹的，他们会判断url的协议头是不是registry
private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
```

ServiceConfig中的暴露逻辑就分析完了，在该类中，主要做了如下几件事：

1. 检查和补充配置信息

2. 多注册中心多协议暴露逻辑，即放在两个方法中的两个循环。

3. 拼接URL对象，获取invoker对象。

4. 将invoker传给protocol进行协议层的export操作。

   这里要提醒的是，在dubbo中，URL是配置信息的核心，Invoker是执行逻辑的核心。Invoker的抽象实现类AbstractInvoker中保存了URL属性，也就保存了方法调用的所有相关配置信息。

如果指定了注册中心地址，则Invoker中保存的url的协议头是“registry://”,一次执行`ProtocolFilterWrapper.export(Invoker invoker)`,`ProtocolListenerWrapper.export(Invoker invoker)`，`QosProtocolWrapper.export(Invoker invoker)`,最终执行到`RegistryProtocol.export(Invoker invoker)`。`RegistryProtocol.export(Invoker invoker)`方法真正的调用了**服务开启**和**服务注册**两个方法。

```java
// RegistryProtocol.java 
// 配置了注册中心时，会使用这个Protocol来执行服务开启和服务注册程序
public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
    // 获取服务注册用的url
    URL registryUrl = getRegistryUrl(originInvoker);
    
    //获取提供服务用的url
    // url to export locally
    URL providerUrl = getProviderUrl(originInvoker);

    // Subscribe the override data
    // FIXME When the provider subscribes, it will affect the scene : a certain JVM exposes the service and call
    //  the same service. Because the subscribed is cached key with the name of the service, it causes the
    //  subscription information to cover.
    final URL overrideSubscribeUrl = getSubscribedOverrideUrl(providerUrl);
    final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
    overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

    providerUrl = overrideUrlWithConfig(providerUrl, overrideSubscribeListener);

    // 这里执行服务开启逻辑
    //export invoker
    final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker, providerUrl);

    // url to registry
    final Registry registry = getRegistry(originInvoker);
    final URL registeredProviderUrl = getRegisteredProviderUrl(providerUrl, registryUrl);
    ProviderInvokerWrapper<T> providerInvokerWrapper = ProviderConsumerRegTable.registerProvider(originInvoker,
            registryUrl, registeredProviderUrl);
    //to judge if we need to delay publish
    boolean register = registeredProviderUrl.getParameter("register", true);
    if (register) {
        // 这里执行服务注册逻辑
        register(registryUrl, registeredProviderUrl);
        providerInvokerWrapper.setReg(true);
    }

    // Deprecated! Subscribe to override rules in 2.6.x or before.
    registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);

    exporter.setRegisterUrl(registeredProviderUrl);
    exporter.setSubscribeUrl(overrideSubscribeUrl);
    //Ensure that a new exporter instance is returned every time export
    return new DestroyableExporter<>(exporter);
}

```



我们先看doLocalExport方法是如何开启服务的。

```java
// RegistryProtocol.java
    private <T> ExporterChangeableWrapper<T> doLocalExport(final Invoker<T> originInvoker, URL providerUrl) {
        String key = getCacheKey(originInvoker);

        return (ExporterChangeableWrapper<T>) bounds.computeIfAbsent(key, s -> {
            Invoker<?> invokerDelegate = new InvokerDelegate<>(originInvoker, providerUrl);
            return new ExporterChangeableWrapper<>((Exporter<T>) protocol.export(invokerDelegate), originInvoker);
        });
    }
```

这里调用了`protocol.export(invokerDelegate)`方法，而InvokerDelegate则是通过providerUrl构建的。因此，会获取providerUrl的协议头，默认为`dubbo://`。因此，再次通过SPI获取DubboProtocol。而这个DubboProtocol实例则会通过代理工厂包裹三层包装，跟上述的本地导出(injvm)一样。执行了三层包装的export方法后，会正式来到`DubboProtocol.export()`方法中。这个方法就是Dubbo开启服务的主方法。



```java
// 终于来到了DubboProtocol.java
// 这里是协议层的服务导出逻辑的入口，注意入参是Invoker
@Override
public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
    // 从invoker中轻松获取全局配置总线url，并组装出一个DubboExporter对象，如果暴露成功，就把这个对象返回
    URL url = invoker.getUrl();
    // export service.
    String key = serviceKey(url);
    DubboExporter<T> exporter = new DubboExporter<T>(invoker, key, exporterMap);
    exporterMap.put(key, exporter);
    //export an stub service for dispatching event
    // 判断存根相关逻辑
    Boolean isStubSupportEvent = url.getParameter(STUB_EVENT_KEY, DEFAULT_STUB_EVENT);
    Boolean isCallbackservice = url.getParameter(IS_CALLBACK_SERVICE, false);
    if (isStubSupportEvent && !isCallbackservice) {
        String stubServiceMethods = url.getParameter(STUB_EVENT_METHODS_KEY);
        if (stubServiceMethods == null || stubServiceMethods.length() == 0) {
            if (logger.isWarnEnabled()) {
                logger.warn(new IllegalStateException("consumer [" + url.getParameter(INTERFACE_KEY) +
                        "], has set stubproxy support event ,but no stub methods founded."));
            }
        } else {
            stubServiceMethodsMap.put(url.getServiceKey(), stubServiceMethods);
        }
    }
    // 这里调用了openServer方法来开启网络服务
    openServer(url);
    optimizeSerialization(url);
    return exporter;
}
```

```java
// DubboProtocol.java
// 判断是否是服务端，以及是否已经开启了服务
private void openServer(URL url) {
    // find server.
    String key = url.getAddress();
    //client can export a service which's only for server to invoke
    boolean isServer = url.getParameter(IS_SERVER_KEY, true);
    if (isServer) {
        // 查看是否已经存在这个服务
        ExchangeServer server = serverMap.get(key);
        if (server == null) {
            synchronized (this) {
                // double-check检查
                server = serverMap.get(key);
                if (server == null) {
                    // 这里调用createServer()开启服务，比较隐蔽哈
                    serverMap.put(key, createServer(url));
                }
            }
        } else {
            // server supports reset, use together with override
            server.reset(url);
        }
    }
}
```

上述方法主要是做了服务是否存在的判断，如果不存在，调用`createServer(url)`方法。

```java
// DubboProtocol.java
// 
private ExchangeServer createServer(URL url) {
    // 向url补充了几个参数
    url = URLBuilder.from(url)
            // send readonly event when server closes, it's enabled by default
            .addParameterIfAbsent(CHANNEL_READONLYEVENT_SENT_KEY, Boolean.TRUE.toString())
            // enable heartbeat by default
            .addParameterIfAbsent(HEARTBEAT_KEY, String.valueOf(DEFAULT_HEARTBEAT))
            .addParameter(CODEC_KEY, DubboCodec.NAME)
            .build();
    // 获取网络服务的关键字，dubbo中默认为netty，然后通过SPI获取到netty的传输层实现
    String str = url.getParameter(SERVER_KEY, DEFAULT_REMOTING_SERVER);
    if (str != null && str.length() > 0 && !ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(str)) {
        throw new RpcException("Unsupported server type: " + str + ", url: " + url);
    }
    ExchangeServer server;
    try {
        // 调用Exchangers.bind方法启动netty服务
        server = Exchangers.bind(url, requestHandler);
    } catch (RemotingException e) {
        throw new RpcException("Fail to start server(url: " + url + ") " + e.getMessage(), e);
    }
    str = url.getParameter(CLIENT_KEY);
    if (str != null && str.length() > 0) {
        Set<String> supportedTypes = ExtensionLoader.getExtensionLoader(Transporter.class).getSupportedExtensions();
        if (!supportedTypes.contains(str)) {
            throw new RpcException("Unsupported client type: " + str);
        }
    }
    return server;
}
```

dubbo协议默认使用netty网络通信框架进行服务接口的暴露。在`DubboProtocol.java`中，最终调用`Exchangers.bind(url)`方法来开启服务。

`Exchangers`类是一个门面类，即**门面模式**,它重载了多个方法，对Exchanger的调用都通过这个类来调用，具体调用哪个Exchanger接口的实现，还是要通过SPI决定。

```java
// Exchangers.java    
public static ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
    	// 交给Exchanger处理，getExchanger方法通过SPI获取Exchanger实例     
    	return getExchanger(url).bind(url, handler);
    }

// Exchangers.java
// SPI获取Exchanger实例，默认实现为HeaderExchanger.java
    public static Exchanger getExchanger(URL url) {
        String type = url.getParameter(Constants.EXCHANGER_KEY, 	Constants.DEFAULT_EXCHANGER);
        return getExchanger(type);
    }

    public static Exchanger getExchanger(String type) {
        return ExtensionLoader.getExtensionLoader(Exchanger.class).getExtension(type);
    }
```

Exchanger的作用是什么？官网这样说：

**exchange 信息交换层**：封装请求响应模式，同步转异步，以 `Request`, `Response` 为中心，扩展接口为 `Exchanger`, `ExchangeChannel`, `ExchangeClient`, `ExchangeServer`

```java
// HeaderExchanger.java
@Override
public ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
    return new HeaderExchangeServer(Transporters.bind(url, new DecodeHandler(new HeaderExchangeHandler(handler))));
}
```

这里调用Transporters.bind(URL url, ChannelHandler... handlers)方法，Transporters与Exchangers类似，都是门面模式的实现。最终会调用NettyTransporter.bind()方法。

```java
// NettyTransporter
    @Override
    public Server bind(URL url, ChannelHandler listener) throws RemotingException {
        return new NettyServer(url, listener);
    }
```

这一路下来，开启了Netty网络服务，使提供者可以被调用。

接下来，将回到上述的`RegistryProtocol.export()`方法中，来继续执行服务注册逻辑。

服务注册逻辑是通过调用`register(registryUrl, registeredProviderUrl);`来开始执行。

```java
//RegistryProtocol.java
    @Override
    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        URL registryUrl = getRegistryUrl(originInvoker);
        // url to export locally
        URL providerUrl = getProviderUrl(originInvoker);

        // Subscribe the override data
        // FIXME When the provider subscribes, it will affect the scene : a certain JVM exposes the service and call
        //  the same service. Because the subscribed is cached key with the name of the service, it causes the
        //  subscription information to cover.
        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(providerUrl);
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

        providerUrl = overrideUrlWithConfig(providerUrl, overrideSubscribeListener);
       
        // 这里启动Netty开启服务
        //export invoker
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker, providerUrl);

        // url to registry
        final Registry registry = getRegistry(originInvoker);
        final URL registeredProviderUrl = getRegisteredProviderUrl(providerUrl, registryUrl);
        ProviderInvokerWrapper<T> providerInvokerWrapper = ProviderConsumerRegTable.registerProvider(originInvoker,
                registryUrl, registeredProviderUrl);
        //to judge if we need to delay publish
        boolean register = registeredProviderUrl.getParameter("register", true);
        if (register) {
            // 这里注册服务
            register(registryUrl, registeredProviderUrl);
            providerInvokerWrapper.setReg(true);
        }

        // Deprecated! Subscribe to override rules in 2.6.x or before.
        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);

        exporter.setRegisterUrl(registeredProviderUrl);
        exporter.setSubscribeUrl(overrideSubscribeUrl);
        //Ensure that a new exporter instance is returned every time export
        return new DestroyableExporter<>(exporter);
    }

    public void register(URL registryUrl, URL registeredProviderUrl) {
        // getRegistry通过SPI获取到Registry实例，如果是Zookeeper作为注册中心，则这个registryFactory实例是ZookeeperRegistryFactory.java
        Registry registry = registryFactory.getRegistry(registryUrl);
        // 执行注册方法
        registry.register(registeredProviderUrl);
    }

```

```java
// RegistryFactory.java
@Adaptive({"protocol"})
    Registry getRegistry(URL url);
```

```java
// ZookeeperRegistry.java
// 最终调用ZookeeperRegistry这个实现的doRegister方法，将url的参数写入zookeeper
@Override
    public void doRegister(URL url) {
        try {
            zkClient.create(toUrlPath(url), url.getParameter(DYNAMIC_KEY, true));
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
```

这之后就是调用zookeeper客户端，将url中的参数解析并发送到zookeeper中。本文就不再讲解。

总结整个服务导出的过程如下：

1. 组装各种配置信息，组成URL，将URL传递给代理工厂生成Invocker对象。
2. 获取相应的实现类`InjvmProtocol.java`,将服务导出到本地。
3. 判断是否制定注册中心，如果没指定，直接通过DubboProtocol开启服务；如果指定了注册中心，则先通过DubboProtocol开启服务，再通过RegistryProtocol进行服务注册。



## 总结

**服务导出** 有三种类型，根据url中的`scope` 可分为 `null-不导出`、`local-本地导出` 和 `remote-远程导出`。

- **null字符串-不导出：** 什么都不用做；
- **local-本地导出：** 供同一JVM下调用；
- **remote-远程导出：** 开启网络服务并将接口暴露到注册中心。

> 三中导出方式并不是互相冲突的关系。假如scope配置成`null字符串`,则不会导出服务，而如果没有配置，则会判断scope的值为空值`null`而不是字符串`null`，此时会同时导出到`local`和`remote`,以同时提供本地调用和远程调用。