# JUC中的线程池

> JDK1.5中新加入了JUC包，用于支持Java多线程编程，线程池则是其中一部分。



## 线程池的类型

JUC提供了Executors工具类来快速创建线程池，具体如下：

`Executors.newSingleThreadExecutor():`单线程的线程池；
`Executors.newFixedThreadPool():` 固定线程的线程池；
`Executors.newCachedThreadPool():` 可以缓存线程的线程池；
`Executors.newScheduledThreadPool()：` 用于执行定时任务的线程池；
`Executors.newSingleThreadScheduledExecutor()：` 用于执行定时任务的单线程线程池；

以上创建线程的方式均不推荐，理由是：使用了无界队列和无限制的最大线程数。

newFixedThreadPool()方法、newCachedThreadPool()方法中使用了`LinkedBlockingQueue`作为阻塞队列，

由于没有指定容量，当消息不断在队列中积累时，最终会导致OOM异常。`newScheduledThreadPool()`方法

和`newSingleThreadScheduledExecutor()`方法中使用的`DelayedWorkQueue`也同样是无界队列，

因为它可以自动扩容，且无法指定最大容量。

因此，正确的使用方法是自己创建`ThreadPoolExecutor`实例，并按需指定参数值。该类的全参数构造方法如下：

```java
    /**
     * 线程池的构造方法
     *
     * @param corePoolSize    核心线程数量
     * @param maximumPoolSize 最大线程数量
     * @param keepAliveTime   空闲线程存活时间
     * @param unit            时间单位
     * @param workQueue       工作队列，用于存放线程需要执行的任务
     * @param threadFactory   线程工厂，用于创建线程实例，放入线程池
     * @param handler         拒绝策略，当工作队列满的情况下的处理策略
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler);
```


## 工作队列的类型

线程池中常见的工作队列有：

- LinkedBlockingQueue: 用一个单链表结构存储数据，支持指定容量。它继承BlockingQueue接口，该接口定义了阻塞功能，即队列为空时阻塞消费线程，队列为满时阻塞生产线程；
- ArrayBlockingQueue: 由数组实现的固定大小的阻塞队列，必须指定容量且不能改变。它同样继承BlockingQueue接口，实现阻塞功能；
- DelayQueue: 延时队列，通过优先级队列（PriorityQueue）实现，用于定时任务，是无界队列，继承BlockingQueue接口；
- DelayWorkQueue: ScheduledThreadPoolExecutor类的内部类，功能与DelayQueue类似，针对定时场景做了部分重写
- SynchronousQueue: 同步队列，该队列中不存放元素，仅仅做同步转发，newSingleThreadExecutor()方法中默认使用此队列。 

以上类均是阻塞队列，继承AbstractQueue抽象类并实现BlockingQueue接口。





