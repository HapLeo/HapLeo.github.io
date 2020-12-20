# Dubbo-负载均衡

## 简介

什么是负载均衡？

在集群中，有多个服务提供者提供相同的服务。当消费者进行RPC调用时，只需要成功的调用其中一个服务提供者即可。如何选择适当的服务提供者则是负载均衡的功能。

## 负载均衡策略

Dubbo中提供了四种负载均衡策略：

- RandomLoadBalance: 基于权重随机算法
- LeastActiveLoadBalance:基于最少活跃调用数算法
- ConsistentHashLoadBalance:基于一致性哈希算法
- RoundRobinLoadBalance:基于加权轮询算法

