# Dubbo-服务目录

## 简介

我们先来思考一个问题：

**在Dubbo中，如果服务提供者的url发生了变化，服务消费者如何感知以保证不会请求过期的url？**

这个问题是集群容错的范畴。

在Dubbo中，集群容错包含四块内容：服务目录Directory、服务路由Router、集群Cluster和负载均衡LoadBalance。

**服务目录中存储了一些和服务提供者有关的信息，通过服务目录，服务消费者可获取到服务提供者的信息，比如 ip、端口、服务协议等。** —— from Dubbo官网

通过Dubbo官网的这句话，我们得知，服务目录Directory负责感知和更新服务提供者的变化。

在一个服务集群中，当服务提供者的数量或配置信息发生变化时，对应到服务目录中的记录也需要改变。

实际上服务目录在从注册中心获取服务提供者列表后，会为每个服务提供者生成一个Invoker实例，并持有一个由这些Invoker实例组成的List集合，而这些Invoker实例就是服务消费者用于发起远程调用的执行器。服务目录负责动态感知注册中心的变化，以维护Invoker实例列表。

## 功能实现

### Directory接口

```java
public interface Directory<T> extends Node {

    /**
     * get service type.
     *
     * @return service type.
     * 获取接口类，一个接口类对应到注册中心里的一个服务
     * 接口类名可以通过Directory持有的invoker实例获取
     */
    Class<T> getInterface();

    /**
     * list invokers.
     * 获取invoker列表
     * @return invokers
     */
    List<Invoker<T>> list(Invocation invocation) throws RpcException;

}
```

该接口定义的方法非常简单，其中`list`方法是获取Invoker列表。

### AbstractDirectory 抽象类

该抽象类实现了一些基础逻辑功能，它实现的`list`方法为模板方法，具体逻辑由子类实现。

```java
// AbstractDirectory.java
    @Override
    public List<Invoker<T>> list(Invocation invocation) throws RpcException {
        if (destroyed) {
            throw new RpcException("Directory already destroyed .url: " + getUrl());
        }

        return doList(invocation);
    }


    protected abstract List<Invoker<T>> doList(Invocation invocation) throws RpcException;

```

具体的Directory实现有两个：`StaticDirectory`和`RegistryDirectory`，从名称可以看出，`StaticDirectory`是静态目录，不可变。而`RegistryDirectory`实现了`NotifyListener`接口，可以根据注册中心的变化而变化。

### StaticDirectory实现类

```java
// StaticDirectory.java
public class StaticDirectory<T> extends AbstractDirectory<T> {
    
    private final List<Invoker<T>> invokers;
    
    
    public StaticDirectory(List<Invoker<T>> invokers) {
        this(null, invokers, null);
    }

    @Override
    protected List<Invoker<T>> doList(Invocation invocation) throws RpcException {
        List<Invoker<T>> finalInvokers = invokers;
        if (routerChain != null) {
            try {
                finalInvokers = routerChain.route(getConsumerUrl(), invocation);
            } catch (Throwable t) {
                logger.error("Failed to execute router: " + getUrl() + ", cause: " + t.getMessage(), t);
            }
        }
        return finalInvokers == null ? Collections.emptyList() : finalInvokers;
    }
}
```

该类持有一个invoker列表，通过final修饰，构造方法注入，也就是说该类的对象一旦创建，就不会对invoker列表进行变化，它也没有感知变化的能力，仅仅是为了保存这个invoker列表而存在。

### RegistryDirectory实现类

```java
// RegistryDirectory.java
    @Override
    public List<Invoker<T>> doList(Invocation invocation) {

        if (multiGroup) {
            return this.invokers == null ? Collections.emptyList() : this.invokers;
        }

        List<Invoker<T>> invokers = null;
        try {
            // Get invokers from cache, only runtime routers will be executed.
            invokers = routerChain.route(getConsumerUrl(), invocation);
        } catch (Throwable t) {
            logger.error("Failed to execute router: " + getUrl() + ", cause: " + t.getMessage(), t);
        }
        return invokers == null ? Collections.emptyList() : invokers;
    }

```

`RegistryDirectory`的`doList`方法与`StaticDirectory`的该方法区别不大，核心逻辑就是返回invoker列表。

核心区别在于`RegistryDirectory`实现了`NotifyListener`接口并实现了`notify()`方法。该方法的核心逻辑是根据注册中心推送的新的url对持有的invokers列表进行更新。

```java
// RegistryDirectory.java
@Override
    public synchronized void notify(List<URL> urls) {
        // 将注册中心通知过来的urls列表进行分类，分为configurator、router和provider三类。
        Map<String, List<URL>> categoryUrls = urls.stream()
                .filter(Objects::nonNull)
                .filter(this::isValidCategory)
                .filter(this::isNotCompatibleFor26x)
                .collect(Collectors.groupingBy(url -> {
                    if (UrlUtils.isConfigurator(url)) {
                        return CONFIGURATORS_CATEGORY;
                    } else if (UrlUtils.isRoute(url)) {
                        return ROUTERS_CATEGORY;
                    } else if (UrlUtils.isProvider(url)) {
                        return PROVIDERS_CATEGORY;
                    }
                    return "";
                }));

        // 更新configurators
        List<URL> configuratorURLs = categoryUrls.getOrDefault(CONFIGURATORS_CATEGORY, Collections.emptyList());
        this.configurators = Configurator.toConfigurators(configuratorURLs).orElse(this.configurators);
		// 更新routers
        List<URL> routerURLs = categoryUrls.getOrDefault(ROUTERS_CATEGORY, Collections.emptyList());
        toRouters(routerURLs).ifPresent(this::addRouters);

        // 更新providers
        List<URL> providerURLs = categoryUrls.getOrDefault(PROVIDERS_CATEGORY, Collections.emptyList());
        refreshOverrideAndInvoker(providerURLs);
    }
```

至此，只是在url层面的操作，那么如何通过url获取到Invoker进而更新invoker列表呢？

通过跟踪`refreshOverrideAndInvoker(providerURLs)`这个方法的调用逻辑，可以找到在`toInvokers(List<URL> urls)`这个方法中有如下语句：

`invoker = new InvokerDelegate<>(protocol.refer(serviceType, url), url, providerUrl);`

也就是说，新的invoker是通过`protocol.refer(serviceType, url)`获取,并包上一层包装类`InvokerDelegate`。这里的`protocol.refer(serviceType, url)`我们已经很熟悉了，就是服务引入过程中用来生成服务提供者代理类invoker的方法。









