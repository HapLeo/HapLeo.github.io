# ubuntu 新装系统必备命令

## 解压缩 tar.gz 后缀的文件

- 命令

  ```
   tar -zxvf abc.tar.gz -C /usr/local/src
  ```

- 说明
  
  > -z 使用gzip进行归档
  >
  > -x  --extract 从归当中提取出来
  >
  > -v  --verbose 详细列出处理的文件 
  >
  > -f  --file=ARCHIVE 使用归档文件或ARCHIVE设备 
  >
  > -C 后面指定解压目录
  >
  > abc.tar.gz为需要解压的文件路径 ，/usr/local/src为解压出的文件存放路径
## 安装远程控制服务 openssh-server

- 说明
  
  > 如果想通过类似于xshell的软件远程连接，需要在服务端（被连接的电脑）安装open-ssh server 
- 命令
``` linux
sudo apt-get install openssh-server
```
- 测试

  > 使用ps查看sshd进程是否存在

```linux
sudo ps -ef|grep sshd
```



## 安装搜狗输入法Ubuntu版

- 说明

  > 步骤1：去搜狗官网下载ubuntu版本的输入法，ubuntu 版本的安装包是.deb后缀的文件；
  >
  > 步骤2：.deb安装包使用dpkg命令进行安装，他是debian系列操作系统的安装包格式；
  >
  > 步骤3：使用apt-get install -f 解决依赖问题;
  >
  > 步骤4：打开语言支持-键盘输入法系统，选择fcitx(搜狗拼音输入法Ubuntu版基于此架构开发);
  >
  > 步骤5：在屏幕右上角"配置当前输入法"，将搜狗输入法移动到首位；

- 命令

  ```
  sudo dpkg -i sougoupinyin_2.3.1_amd64.deb
  ```

  > -i|--install :安装该文件;

  ```
  sudo apt-get install -f
  ```

  执行完上述命令后，按照步骤4修改输入法的架构，再按照步骤5修改优先使用的输入法。



## ubuntu系统安装docker

- 命令

  ```
sudo apt-get install docker.io
  ```
  
  