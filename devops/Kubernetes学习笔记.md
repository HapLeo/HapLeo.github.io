# Kubernetes学习笔记

## 1.1 什么是Kubernetes?

- Kubernetes 是**基于容器技术的**分布式架构方案；

  > 目的是实现资源管理的自动化，和跨数据中心的资源利用率最大化；
  >
  > 提供负载均衡、服务治理、服务监控、故障处理功能；

- Kubernetes是一个开放的开发平台；

  >不限于任何语言和编程接口；
  >
  >将服务模块映射为Kubernetes的Service,Service之间通过标准的TCP通信协议进行交互；

- Kubernetes是一个完备的分布式系统支撑平台；

  > 具有完备的集群管理能力，包括安全防护和准入机制、多租户应用支撑能力、透明的服务注册和发现机制、内建的智能负载均衡器、强大的故障发现和自我修复能力、服务滚动升级和在线扩容能力、可扩展的资源自动调度机制，以及多里读的资源配额管理能力；
  >
  > 提供了完善的管理工具，涵盖开发、部署测试、运维监控各个环节。

- Kubernetes的核心——Service

  > 每个Service拥有唯一指定的名称（比如mysql-server）；
  >
  > 每个Service拥有一个虚拟IP和端口号；
  >
  > 能够作为一个整体提供某种远程服务能力；
  >
  > 由**一组**提供服务能力的容器组成；
  >
  > 通俗地讲，一个Service就是一个逻辑服务，组成上类似于nginx + 多台MySQL组成的MySql服务；

- 几个概念：

  - Master
  
    > 集群中的管理者;
    >
    > 相当于一台主机，负责管理所有Node;
    >
    > 通过kube-apiserver、kube-controller-manager和kube-scheduler 三个进程实现整个集群的资源管理、Pod调度、弹性伸缩、安全控制、系统监控和纠错等；
  
  - Node
  
    > 集群中的节点,运行用户的进程；
    >
    > 每个Node是一台计算机，每个Node上会运行几百个Pod;
    >
    > 通过kubelet和kube-proxy两个进程负责Pod的创建、启动、监控、重启、销毁、负载均衡；
  
  - Pod
  
    > K8s管理的最小单位；
    >
    > Pod内部运行着Pause容器和其他业务容器，业务容器共享Pause容器的网络和Volume挂载卷；
    >
    > 因为同一Pod内的容器共享资源，因此通常讲需要通信和数据交换的(一组密切相关的)容器放到一个Pod中，以提高效率；                                                                                                                                                                                                                                                                                                                                                                   

sudo apt update && sudo apt install -y apt-transport-https curl
curl -s https://mirrors.aliyun.com/kubernetes/apt/doc/apt-key.gpg | sudo apt-key add -

```
echo "deb https://mirrors.aliyun.com/kubernetes/apt/ kubernetes-xenial main" >>/etc/apt/sources.list.d/kubernetes.list
```

```kotlin
kubeadm init \
--apiserver-advertise-address=192.168.3.3 \
--image-repository registry.aliyuncs.com/google_containers \
--pod-network-cidr=10.244.0.0/16
```

kubeadm join 192.168.3.3:6443 --token ce4iae.p0vt123o5g2cz7m1 \
    --discovery-token-ca-cert-hash sha256:e27a95951a9e56ddacbcfc44befb47c8ae7b9d1138a9f4e721ffabee244c1299 



## 安装与配置

#### 优秀的博客：https://www.jianshu.com/p/f2d4dd4d1fb1







## 小记

### 1. 虚拟机网络的NAT模式与桥接模式的区别是什么？

NAT(Network Address Translation——网络地址转换): 通过宿主机所在的网络来访问网络，不分配独立的局域网IP；

桥接模式(Bridged Networking)：可以获取宿主机所在局域网中的独立IP地址；



## 1.2 Kubernetes的组件

> 说明：**资源**：Kubenetes中的Node、Pod、Replication Controller、Service等都是资源对象，所有资源都可以通过yml或json格式的文件来定义，资源对象状态都保存在etcd数据库中。

### 1.2.1 Master

Master是集群控制节点，负责管理整个集群，运维人员对整个集群的操作也都是基于传递指令到Master。一个Master通常占用一台独立的服务器，通常会部署三台Master防止单点故障。

Master节点中的进程：

- kube-apiserver: 集群控制的入口进程，提供了Http Rest接口，可以对集群内所有资源进行增删改查操作；
- kube-controller-manager: 集群内所有资源的自动化控制器，包括对Node节点的监控，负责具体操作的实施；
- kube-scheduler: 调度器，负责对Pod资源的调度；
- etcd: 一个分布式数据库服务，保存集群中所有资源对象信息；

### 1.2.2 Node

Kubernetes集群中，Master以外的节点，都叫做Node。Node是集群中的工作节点，负责运行我们的程序。

Node节点中的进程：

- kubelet: 负责与Master通信，执行容器的创建、启停等任务。
- kube-proxy: 负责Service内的负载均衡。
- Docker Engine: 因为Kubenetes基于容器技术，因此Node节点也需要安装docker引擎来生产和管理容器。

- Node节点安装了以上必须要的程序后，会通过kubelet向Master主动注册自己，注册成功后，kubelet进程会定时向Master汇报当前Node节点的整体情况，包括操作系统、docker版本、Pod运行情况、CPU以及内存的使用情况。如果超时未报，则Master会将该节点标记为Not Ready,进而触发**Pod驱逐**。

### 1.2.3 Pod

Pod是Kubernetes中的最小运行单位，一个Pod中包含一个Pause容器和多个密切相关的业务容器，一个Node节点又可以部署多个Pod。在Pod内部，Pause容器是这个Pod的根容器，具有Kubernetes定制的IP地址（Pod IP）和挂载的磁盘Volume,而其他业务容器则共享Pause容器的网络和磁盘，以高效的进行业务间通信和文件共享。另外，通过借助Flannel等工具，Kubernetes要求任意两个Pod之间都能进行TCP/IP直接通信。

当Pod内某个容器停止时，Kubernetes会重启整个Pod（重启Pod中的所有容器),当整个Node宕机时，Kubernetes会讲这个Node上的所有Pod调度到其他节点上。

Pod可以为它的每个容器指定最小(最大)cpu使用量和最小(最大)内存使用量，一旦容器突破最大使用量限制，则会被kill并重启。

### 1.2.4 Label

Label即标签，是加在各种资源对象上的键值对，key和value均由用户自己指定，一个资源可以加多个标签，同时一个标签也可以加在多种资源上。标签便于对资源进行多维度的分组管理。

Label Selector(标签选择器)通过标签可以选出对应的资源对象，例如：

- kube-controller通过在Replication Controller上定义的Label Seletor 筛选出它负责监控的Pod副本，以维持预期数量。

- kube-proxy通过Service的Label Selector筛选出对应Pod列表，以建立Service到Pod的转发路由表，实现Service的负载均衡。

### 1.2.5 Replication Controller(RC)

RC用于定义指定标签的Pod的数量和创建新Pod的模板。Master上的Controller Manager会根据RC中定义的Pod数量定期检查，以确保存活指定数量的Pod。通过``kubectl scale rc`` 命令，可以动态扩容或缩容。RC还可以通过指定版本号来进行滚动升级。