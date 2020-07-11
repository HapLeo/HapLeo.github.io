# 缓存
## 缓存分类
mybatis的缓存分为本地缓存（也叫一级缓存）和二级缓存，一级缓存的作用域是session级别，当commit时刷新缓存；
二级缓存是namespace级别，当session close时刷新缓存；

## 缓存配置
一级缓存默认开启，两种作用域：SESSION和STATEMENT;
二级缓存默认关闭，可使用<cache/>开启，全局配置enableCache=false可以关闭所有二级缓存，默认为true;


## 注意点
1. 通常不会将连表查询语句进行缓存，因为会引起二级缓存的脏读；
1. 一级缓存默认开启级别为SESSION，因此不同SESSION读取一级缓存会出现脏读，可将一级缓存级别设为STATEMENT以避免脏读；
2. 二级缓存默认不开启，可使用<cache/>标签放入mapper.xml中开启对应namespace的二级缓存，二级缓存下的关联查询可能会引起脏读，
因为涉及到不同的namespace，无法相互感知。
3. 通常不推荐使用mybatis的一级缓存和二级缓存；

## 缓存原理

## 如何配置redis作为缓存



# 拦截器
  ## 概念：mybatis提供plugin功能，对Executor/ParameterHandler/StatementHandler/ResultSetHandler四大接口的部分方法进行拦截，
  以增加自己的业务逻辑；

  ## 实现：mybatis提供了Interceptor接口，实现这个接口，覆盖相应的方法，并通过@Intercepts注解表名这是一个mybatis拦截器，
  并通过<plugins>标签将该拦截器配置到全局配置文件中，当mybatis解析配置文件时，会将这个plugin配置到执行流程中。

  ## 案例：Mybatis-PageHelper分页插件就是利用plugin实现的，参见github源码。

  ## 拦截器原理

# 懒加载
  ## 配置：全局配置中指定<setting name="lazyLoadingEnabled" value="true" />
  mapper.xml中配置resultMap,通过<association>或<collection>标签，需指定column指定关联字段和select属性指定懒加载语句

  ## 注意点
  懒加载通常会提高查询效率，但是也有可能降低查询效率。当查询出10条记录后，需要每条记录的属性进行懒加载，此时比起直接关联查询要多出很多IO操作；

  ## 懒加载原理