

# Dubbo-SPI

SPI全称为`Service Provider Interface`,是一种服务发现机制。

Dubbo中的SPI由Java的SPI扩展而来。

OCP原则：对扩展开放，对修改封闭。

dubbo采用 **微内核+插件架构**，面向功能拆分，可扩展架构。内核负责管理插件的生命周期，不会因为系统功能变化而变化。所有功能实现都通过插件提供。

**思考：**为什么框架需要使用SPI机制？

**举例说明：** JDK提供了java.sql.Driver接口，该接口为不同的数据库厂商提供了统一的连接规范。每个数据库厂商提供这个接口的实现就可以连接不同的数据库。现在，我要写一个工具类，来实现一些自动查询的功能。要实现查询功能，是需要Driver的实例对象的，那么我应该如何获取Driver接口的实例对象呢？答案是由各个厂商的驱动包来提供。那么，当我引入myql的驱动包`jdbc-mysql-connector-*.jar`后，如何才能让我的工具类获取到mysql的Driver实例呢？在mysql的驱动包中，有一个文件夹`META-INF/services`,这个文件夹中保存了mysql厂商提供的Driver具体实现类的名称。我的工具要想使用到具体的Driver实例，只需要扫描这个文件夹，获取到相应的实现类名，就可以通过类加载器把该类加载进来并通过反射实例化对象出来。这样一来，不管是mysql还是Oracle，只要在它的驱动包里的这个目录下放入实现类名称，我的工具类就可以获取到它的实例对象并进行下一步调用了。这就是SPI的插件机制。

Dubbo没有完全采用JDK的SPI机制，而是对它做了增强。

**因此，SPI是一种通知机制，用于下层实现通知上层接口它的实现类的位置。**

> Q: 什么是SPI机制，dubbo是如何实现SPI的？
>
> K: 插件机制，JDK中的ServiceLoader,Dubbo中的ExtensionLoader
>
> A：SPI是一种插件机制，将接口与实现解耦，从而可以动态更改功能的实现。JDK的SPI通过在实现类的包中`META-INF\services`目录下存放文件，通用接口通过ServiceLoader加载指定的类来获取的实例。Dubbo对SPI机制进行了增强，通过关键字在运行时读取`META-INF\dubbo`目录下文件来加载指定的实现类。Dubbo中在一次调用中使用哪种协议、哪种负载均衡策略等都是通过Dubbo的SPI机制加载。

Java中的SPI是通过`ServiceLoader`类的`load`方法对`META-INF\services`目录进行扫描，获取该目录下的文件。这些文件的文件名是服务的接口名，文件的内容则是接口实现类的类名列表。ServiceLoader获取这些实现类的类名后就会自动创建这些类的对象。通过这种方法，我们可以通过配置文件来动态指定某个服务使用哪个具体的实现类。

这里粘贴Dubbo官网的示例来演示：

```java
public interface Robot {
    void sayHello();
}

public class OptimusPrime implements Robot {
    
    @Override
    public void sayHello() {
        System.out.println("Hello, I am Optimus Prime.");
    }
}

public class Bumblebee implements Robot {

    @Override
    public void sayHello() {
        System.out.println("Hello, I am Bumblebee.");
    }
}

// 在META-INF/services目录下放置一个文件，名称为Robot类的全限定名，内容为这两个实现类的全限定名

// 测试代码
public class JavaSPITest {

    @Test
    public void sayHello() throws Exception {
        ServiceLoader<Robot> serviceLoader = ServiceLoader.load(Robot.class);
        System.out.println("Java SPI");
        serviceLoader.forEach(Robot::sayHello);
    }
}
```

以上的测试代码会逐个执行文件内容里指定的每个实现类的sayHello方法。

Dubbo的SPI与Java类似，只是将文件内容从列表变成了`key-value`形式，来达到按需加载的目的。

示例：

```
optimusPrime = org.apache.spi.OptimusPrime
bumblebee = org.apache.spi.Bumblebee
```

需要注意的是，Robot接口上需要指定注解`@SPI` 

```java
public class DubboSPITest {

    @Test
    public void sayHello() throws Exception {
        ExtensionLoader<Robot> extensionLoader = 
            ExtensionLoader.getExtensionLoader(Robot.class);
        Robot optimusPrime = extensionLoader.getExtension("optimusPrime");
        optimusPrime.sayHello();
        Robot bumblebee = extensionLoader.getExtension("bumblebee");
        bumblebee.sayHello();
    }
}
```

从上述测试代码可知，Dubbo的SPI可以通过指定key来获取某个实现类，这样就可以按需加载了。

那么问题来了，如果一个实现类依赖另一个实现类，该如何初始化呢？

我们看一下`ExtensionLoader.getExtension()`方法的源码。

```java
// ExtensionLoader.java
public T getExtension(String name) {
    if (StringUtils.isEmpty(name)) {
        throw new IllegalArgumentException("Extension name == null");
    }
    if ("true".equals(name)) {
        // 获取默认的实现
        return getDefaultExtension();
    }
    final Holder<Object> holder = getOrCreateHolder(name);
    Object instance = holder.get();
    if (instance == null) {
        synchronized (holder) {
            instance = holder.get();
            if (instance == null) {
                // 没有这个实例，创建
                instance = createExtension(name);
                holder.set(instance);
            }
        }
    }
    return (T) instance;
}
```

上面的逻辑先检查是否要获取默认的实现，不是的话通过double-check加锁创建指定的实例。

我们重点看下创建的方法`createExtension()`。

```java


private T createExtension(String name) {
    Class<?> clazz = getExtensionClasses().get(name);
    if (clazz == null) {
        throw findException(name);
    }
    try {
        T instance = (T) EXTENSION_INSTANCES.get(clazz);
        if (instance == null) {
            // 这里创建实现类的实例对象，并放入缓存
            EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
            instance = (T) EXTENSION_INSTANCES.get(clazz);
        }
        // 注入依赖的扩展对象
        injectExtension(instance);
        // 这里对实例进行包装，最后返回的是包装类实例
        Set<Class<?>> wrapperClasses = cachedWrapperClasses;
        if (CollectionUtils.isNotEmpty(wrapperClasses)) {
            for (Class<?> wrapperClass : wrapperClasses) {
                instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
            }
        }
        // 返回包装类实例
        return instance;
    } catch (Throwable t) {
        throw new IllegalStateException("Extension instance (name: " + name + ", class: " +
                type + ") couldn't be instantiated: " + t.getMessage(), t);
    }
}
```

分析代码可知，该方法执行如下逻辑：

1. 从缓存中获取这个实例，如果没取到就创建实例`instance`；
2. 为该实例注入依赖对象；
3. 包装这个实例，最后返回包装类的实例；

继续分析注入依赖的方法`   injectExtension(instance)`:

```java
// ExtensionLoader.java
private T injectExtension(T instance) {
    try {
        if (objectFactory != null) {
            for (Method method : instance.getClass().getMethods()) {
                if (isSetter(method)) {
                    /**
                     * Check {@link DisableInject} to see if we need auto injection for this property
                     */
                    if (method.getAnnotation(DisableInject.class) != null) {
                        continue;
                    }
                    Class<?> pt = method.getParameterTypes()[0];
                    if (ReflectUtils.isPrimitives(pt)) {
                        continue;
                    }
                    try {
                        String property = getSetterProperty(method);
                        Object object = objectFactory.getExtension(pt, property);
                        if (object != null) {
                            method.invoke(instance, object);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to inject via method " + method.getName()
                                + " of interface " + type.getName() + ": " + e.getMessage(), e);
                    }
                }
            }
        }
    } catch (Exception e) {
        logger.error(e.getMessage(), e);
    }
    return instance;
}
```





附录：自适应扩展机制生成的代理工厂接口的代理类源码(这是自适应扩展的核心原理)：

```java
public class ProxyFactory$Adaptive implements org.apache.dubbo.rpc.ProxyFactory {
    public java.lang.Object getProxy(org.apache.dubbo.rpc.Invoker arg0) throws org.apache.dubbo.rpc.RpcException {
        if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
        if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
        org.apache.dubbo.common.URL url = arg0.getUrl();
        String extName = url.getParameter("proxy", "javassist");
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
        org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
        return extension.getProxy(arg0);
    }
    public java.lang.Object getProxy(org.apache.dubbo.rpc.Invoker arg0, boolean arg1) throws org.apache.dubbo.rpc.RpcException {
        if (arg0 == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument == null");
        if (arg0.getUrl() == null) throw new IllegalArgumentException("org.apache.dubbo.rpc.Invoker argument getUrl() == null");
        org.apache.dubbo.common.URL url = arg0.getUrl();
        String extName = url.getParameter("proxy", "javassist");
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
        org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
        return extension.getProxy(arg0, arg1);
    }
    public org.apache.dubbo.rpc.Invoker getInvoker(java.lang.Object arg0, java.lang.Class arg1, org.apache.dubbo.common.URL arg2) throws org.apache.dubbo.rpc.RpcException {
        if (arg2 == null) throw new IllegalArgumentException("url == null");
        org.apache.dubbo.common.URL url = arg2;
        String extName = url.getParameter("proxy", "javassist");
        if(extName == null) throw new IllegalStateException("Failed to get extension (org.apache.dubbo.rpc.ProxyFactory) name from url (" + url.toString() + ") use keys([proxy])");
        org.apache.dubbo.rpc.ProxyFactory extension = (org.apache.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(org.apache.dubbo.rpc.ProxyFactory.class).getExtension(extName);
        return extension.getInvoker(arg0, arg1, arg2);
    }
}
```

