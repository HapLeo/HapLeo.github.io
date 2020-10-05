代理模式是23种经典设计模式之一，也是框架中频繁使用的一种设计模式。

代理模式是使用一个代理类来持有业务逻辑类，调用的时候不直接调用业务逻辑类而是调用代理类，通过代理类来间接调用业务逻辑类的方式，达到在业务逻辑方法调用前后方便的增加一些通用逻辑的目的。

为每个类手动写代理类的方法被称作静态代理。静态代理会产生很多代理类，于是出现了动态代理来解决这个问题。

动态代理的思想是指定某个类作为代理类，调用该类的某个方法，将业务逻辑类的实例对象、方法名、参数列表传给这个代理类的方法，这个方法通过反射的方式调用业务逻辑类中指定的方法，来达到代理的目的。这样，一个类就搞定了所有需要代理的业务逻辑了。

**JDK动态代理**

JDK提供了一种动态代理的方案。实现`InvocationHandler`接口即可。

```java
// InvocationHandler.java
public interface InvocationHandler {
    
    public Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable;
}

```

该接口只定义了一个方法：`invoke`方法，传入被代理的对象、需要调用的方法和参数列表。

也就是说，JDK的动态代理只是给我们提供了一个实现动态代理类的统一规范。下面我们来实现一个动态代理类，来看一下JDK的动态代理类如何使用。

```java
// 首先定义一个接口（JDK动态代理基于接口实现）
public interface IUserService {

    void deleteById(Integer userId);
}
```

```java
// 在定义一个正常的实现类
public class UserServiceImpl implements IUserService {

    @Override
    public void deleteById(Integer userId) {
        System.out.println("删除了一个用户,id=" + userId);
    }
}
```

```java
// 定义一个Handler来实现InvocationHandler,这里定义了代理类的业务逻辑
public class ServiceHandler implements InvocationHandler {

    // 持有业务逻辑对象
    private Object target;

    public ServiceHandler(Object target) {
        this.target = target;
    }

    // 代理类的业务逻辑定义在invoke方法中
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("调用业务逻辑类之前，做点什么...");
        Object invoke = method.invoke(target, args);
        System.out.println("调用业务逻辑类之后，做点什么...");
        return invoke;
    }

    // 返回代理对象
    // 这里通过反射基于目标类的接口自动生成了一个代理类并实例化出代理对象
    public Object getProxy() {
        
        ClassLoader classLoader = target.getClass().getClassLoader();
        return Proxy.newProxyInstance(classLoader, target.getClass().getInterfaces(), this);
    }
}
```



```java
// 调用
public static void main(String[] args) throws Throwable {

        IUserService userService = new UserServiceImpl();

        // 普通的调用方法
        userService.deleteById(1);

        // 通过代理调用方法:先获取代理类，在调用代理的业务方法
        IUserService userServiceProxy = (IUserService) new ServiceHandler(userService).getProxy();
        userServiceProxy.deleteById(2);
    }
```

