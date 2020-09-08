# Dubbo-服务引入

## What

**服务引入** 是指服务消费者获取服务提供者的服务实例的过程。

## Why 

在RPC框架中，服务消费者需要获取服务提供者提供的服务实例对象，才能发起RPC调用，就像本地调用时需要服务的实例对象一样。

## How

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

**服务引入的过程总体可分为：组装URL、检查注册中心、生成Invoker、创建代理对象 这四个步骤。**

- 组装URL： 在Dubbo中，URL是配置总线，保存了所有需要的配置信息，并作为参数传递。 
- 检查注册中心：