# Dubbo - 初始化过程

## provider的初始化

- 由ServiceConfig对象创建触发该类的加载（在加载类的初始化阶段触发**SPI**来初始化static属性，如protocol、proxy_factory等）
- 调用`ServiceConfig.export()`实例方法。

Dubbo中provider的初始化是从`ServiceConfig.export()`实例方法开始的。

如果使用API方式，则需要手动调用`export()`方法。

如果使用spring容器，则是通过设置Spring监听器来监听`ContextRefreshEvent`事件以触发`export()`方法的调用。

>  这个监听器是位于dubbo-config模块下的`ServiceBean`,该类实现了`ApplicationListener`接口的`onApplicationEvent()`方法。



## Consumer的初始化



