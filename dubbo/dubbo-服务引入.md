# Dubbo-服务引入

**服务引入** 是指服务消费者获取服务提供者的服务实例的过程。

在RPC框架中，服务消费者需要获取服务提供者提供的服务实例对象，才能发起RPC调用，就像本地调用时需要服务的实例对象一样。

Dubbo的服务引入是从`ReferenceConfig.get()`开始的。无论使用Spring或者其他，最终都会通过调用`ReferenceConfig.get()`来执行服务引入。

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

**服务引入的核心是：创建服务接口的代理对象。**

**服务引入的过程总体可分为：组装URL、生成Invoker、创建代理对象 这三个步骤。**

- 组装URL： 在Dubbo中，URL是配置总线，保存了所有需要的配置信息，并作为参数传递。 因此，服务引入的第一步便是收集配置信息并组装成URL。
- 生成Invoker:invoker是一个执行器实例，服务消费者的Invoker实例用于**执行远程调用**，由Protocol的实现类通过URL构建得到。这里的Protocol实现类则通过`自适应扩展机制`获取。
- 创建代理对象：通过`ProxyFactory.getProxy(invoker)`方法返回服务接口的代理实现对象。

**1. 组装URL**

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

在ReferenceConfig中，get方法被`synchonized`修饰，保证线程安全性。`ref`是ReferenceConfig的属性，该属性就是`main`方法中想要获取的服务实现类的对象（这里返回的其实是代理类的对象）。这里直接返回，是因为`init()`方法已经为该字段赋值。

进入`init()`方法，简化后的代码如下：

```java
// ServiceConfig.java
private void init() {

    // 将需要的配置参数收集到这个map中,待后续转换成url
    Map<String, String> map = new HashMap<String, String>();
    map.put(SIDE_KEY, CONSUMER_SIDE);
    ...
    // 这里创建代理类，传入map返回服务接口的代理类的实例
    ref = createProxy(map);
}
```

由上面分析可知，`init()`方法做了两件事，一是收集配置信息到map中，二是调用`createProxy(map)`方法并将返回值赋值给`ServiceConfig`的`ref`属性。至此，准备完毕，接下来要分析的`createProxy(map)`方法封装了上述 **服务引用** 过程的三个关键步骤：`组装url`、`创建Invoker`、`创建代理对象`。

进入`createProxy`方法，简化后的代码如下：

```java
// ServiceConfig.java
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

可以看到，`createProxy`方法判断了不同情况下的操作逻辑，但每一种逻辑都执行了上述三个步骤。

判断逻辑是用来区分用户希望如何引用服务：

1. map中包含本地引用的信息，则会通过JVM引用；
2. ServiceConfig的url属性不为空，说明用户可能想要通过url直连服务提供者，将整理好的url放入urls集合中。
3. 如果url为空，则从注册中心获取服务提供者的url，同样将整理好的url放入urls集合中。
4. 如果urls集合内只有一个元素，通过Porotocol生成Invoker实例；否则，循环每一个url，通过Protocol生成Invokers实例列表，再使用CLUSTER将多个invoker实例融合成一个Invoker实例，这个实例具有集群的特性。
5. 最后通过代理工厂`ProxyFactory.getProxy(invoker)`方法返回代理对象。