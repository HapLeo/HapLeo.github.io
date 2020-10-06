**摘要** 

1. 普通的代理模式通过手写代理类，使代理类与业务类实现统一接口且代理类持有业务类的方式实现代理。
2. 普通的代理模式需要为不同的接口分别创建代理类，因此，相同的代理逻辑会创建大量重复的代理类，JDK动态代理动态生成代理类，使相同代理逻辑只写一次。
3. JDK动态代理只能代理实现了接口的实现类，CGLib则通过生成业务类的子类来实现代理功能。这种方式的缺点是无法代理final类和final方法，因此，可以与JDK动态代理结合使用。

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

**CGLib**

CGLib是一个字节码生成库，它允许我们在运行时对字节码进行修改和动态生成。

CGLib通过为业务类生成子类的方式实现动态代理，因此无法对final类和final方法进行代理。 

CGLib的核心类是`Enhancer`增强器 和 `MethodInterceptor`方法拦截器。利用这两个类就可以对一个业务类进行动态代理。示例如下：

```java
public static void main(String[] args) {
    Enhancer enhancer = new Enhancer();
    //设置业务类（需要被代理的类）
    enhancer.setSuperclass(UserServiceImpl.class);
    // 设置回调方法（代理逻辑）
    enhancer.setCallback(new MethodInterceptor() {
        @Override
        public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
            System.out.println("前置处理...");
            Object result = methodProxy.invokeSuper(o, objects);
            System.out.println("后置处理...");
            return result;
        }
    });
    UserServiceImpl userService = (UserServiceImpl) enhancer.create();
    userService.deleteById(1);
}
```

**Javassist**

Javasist是一个开源的Java字节码类库，可以简单、快速的生成或修改类。

