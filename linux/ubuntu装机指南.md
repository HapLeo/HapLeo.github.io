## ubuntu装机指南



### 1. 为apt配置阿里云source

打开/etc/apt/source.list文件，将原来的源全部注释，粘贴如下源：

ubuntu 16.04

> deb http://mirrors.aliyun.com/ubuntu/ xenial main
> deb-src http://mirrors.aliyun.com/ubuntu/ xenial main
>
> deb http://mirrors.aliyun.com/ubuntu/ xenial-updates main
> deb-src http://mirrors.aliyun.com/ubuntu/ xenial-updates main
>
> deb http://mirrors.aliyun.com/ubuntu/ xenial universe
> deb-src http://mirrors.aliyun.com/ubuntu/ xenial universe
> deb http://mirrors.aliyun.com/ubuntu/ xenial-updates universe
> deb-src http://mirrors.aliyun.com/ubuntu/ xenial-updates universe
>
> deb http://mirrors.aliyun.com/ubuntu/ xenial-security main
> deb-src http://mirrors.aliyun.com/ubuntu/ xenial-security main
> deb http://mirrors.aliyun.com/ubuntu/ xenial-security universe
> deb-src http://mirrors.aliyun.com/ubuntu/ xenial-security universe

ubuntu 18.04

> deb http://mirrors.aliyun.com/ubuntu/ bionic main restricted universe multiverse
> deb-src http://mirrors.aliyun.com/ubuntu/ bionic main restricted universe multiverse
>
> deb http://mirrors.aliyun.com/ubuntu/ bionic-security main restricted universe multiverse
> deb-src http://mirrors.aliyun.com/ubuntu/ bionic-security main restricted universe multiverse
>
> deb http://mirrors.aliyun.com/ubuntu/ bionic-updates main restricted universe multiverse
> deb-src http://mirrors.aliyun.com/ubuntu/ bionic-updates main restricted universe multiverse
>
> deb http://mirrors.aliyun.com/ubuntu/ bionic-proposed main restricted universe multiverse
> deb-src http://mirrors.aliyun.com/ubuntu/ bionic-proposed main restricted universe multiverse
>
> deb http://mirrors.aliyun.com/ubuntu/ bionic-backports main restricted universe multiverse
> deb-src http://mirrors.aliyun.com/ubuntu/ bionic-backports main restricted universe multiverse



### 2. 安装openssh服务作为ssh服务器

> 用于支持其他电脑远程连接此电脑

```sheel
# 安装服务
sudo apt install openssh-server
# 启动服务
sudo service ssh start
# 验证服务是否启动成功
ps -ef|grep 'ssh'
```



### 3. 设置合盖不休眠

1. 打开文件：/etc/systemd/logind.conf
2. 修改参数：HandleLidSwitch=suspend 改为 HandleLidSwitch=ignore
3. 执行重启系统服务命令：`systemctl restart systemd-logind`



### 4. 安装docker

1. 卸载旧版本

   ```shell
   $ sudo apt-get remove docker docker-engine docker.io containerd runc
   ```

2. 更新apt包索引

   ```shell
   $ sudo apt-get update
   ```

3. 安装apt依赖包，用于通过https来获取仓库

   ```shell
   $ sudo apt-get install \
     apt-transport-https \
     ca-certificates \
     curl \
     gnupg-agent \
     software-properties-common
   ```

   

4. 添加Docker的官方GPG密钥

   ```shell
   $ curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
   ```

9DC8 5822 9FC7 DD38 854A E2D8 8D81 803C 0EBF CD88 通过搜索指纹的后8个字符，验证您现在是否拥有带有指纹的密钥。

```sheel
$ sudo apt-key fingerprint 0EBFCD88
```

使用以下指令设置稳定版仓库

```shell
$ sudo add-apt-repository \
   "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
   $(lsb_release -cs) \
   stable"
```

再更新一下apt包索引：
`apt-get update`

安装最新版本的 Docker Engine-Community 和 containerd

```
$ sudo apt-get install docker-ce docker-ce-cli containerd.io
```



安装完成 检验是否安装成功
`docker version`:显示docker版本信息
`docker run hello-world`:运行下hello-world检验下是否运行成功



### 5. 为docker配置阿里云镜像加速器

```shell
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<-'EOF'
{
  "registry-mirrors": ["https://kfb25oqw.mirror.aliyuncs.com"]
}
EOF
sudo systemctl daemon-reload
sudo systemctl restart docker
```



### 6. docker 安装MySQL

拉取官方的最新版本的镜像：

```
$ docker pull mysql:latest
```

安装完成后，我们可以使用以下命令来运行 mysql 容器：

```
$ docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root --name mysql-server mysql
```

>docker run mysql 是命令主体，让docker运行mysql镜像，如果本地没有mysql镜像，则会自动从dockerHub服务器下载；
>带横线的都是参数名，后面跟着参数值，单横线是缩写，双横线是全拼；
>
>-d: Run container in background and print container ID，后台运行容器 ，是--detach的缩写；
>-p: Publish a container’s port(s) to the host，指定主机端口与容器端口的映射，格式为：主机端口:容器端口，是--publish的缩写；
>-P: Publish all exposed ports to random ports,随机一个主机端口与容器进行映射，此时无需指定参数值；是--publish-all的缩写;
>-e: Set environment variables，给容器指定环境变量，是--env的缩写；
>为mysql指定ROOT用户的密码，格式:**-e MYSQL_ROOT_PASSWORD=yourpassword**;
>--name: Assign a name to the container,给容器指定一个名字，这个名字可以在之后对容器操作时代替容器id使用，方便操作；

此命令默认拉取mysql最新版本镜像并运行，执行成功后可以通过如下命令进入mysql命令行：

- 进入容器                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 

```shell
docker exec -it mysql-server /bin/bash
```

>Run a command in a running container
>docker exec mysqlserver  /bin/bash 是命令主体，让docker 在名称为mysqlserver的容器中运行 /bin/bash命令；
>
>--interactive , -i,Keep STDIN open even if not attached 以交互模式运行容器
>--tty , -t,Allocate a pseudo-TTY 分配一个伪终端

- 进入mysql命令行

```shell
mysql -u root -p
```

>在docker容器的伪终端登录mysql，-u指定用户名为root，-p指的是password，但是后面不要跟你的密码，回车后会提示输入密码，此时输入的密码为隐藏状态；
>密码输入正确后，将进入mysql的欢迎界面；

- 修改密码

  ```mysql
  ALTER user 'root'@'%' IDENTIFIED WITH mysql_native_password BY 'root'; 
  ```



### 7. 搭建FTP服务器

- 安装vsftpd

  ```shell
  sudo apt install vsftp
  ```
  
- 修改配置允许登录用户写入文件
  ```shell
  sudo vim /etc/vsftpd.conf
  # 打开之后将write_enable选项值改为YES
  # 重启服务
  service vsftpd restart
  ```

  安装成功后，即可通过windows的资源管理器进行访问：

  ```shell
  ftp://192.168.3.7   #这里填写Ubuntu主机的IP地址
  ```

  选择Ubuntu系统的用户并进行登录，会进入相应的目录


- 如需卸载，请用如下命令：

  ```shell
  sudo apt-get remove --purge vsftpd
  ```

  (--purge 选项表示彻底删除改软件和相关文件)

- 更多配置请参考：https://www.iteye.com/blog/zyjustin9-2178943 

  