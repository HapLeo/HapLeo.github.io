# MySQL基本配置

## MySQL程序结构

- MySQL是CS架构，客户端进程默认名称为mysql，服务端进程默认名称为mysqld。
- Linux系统下，通常将MySQL安装在/usr/local/目录下，此时/usr/local/mysql/bin目录下可见各种客户端与服务器端程序,例如：mysql,mysql.server,mysqladmin,mysqld,mysqld_safe,mysqlbinlog,mysqlcheck等。

## 程序启停

- Linux下如何启动MySQL服务器：
  - 运行mysqld程序，不常用；
  - 运行mysqld_safe：mysqld_safe是一个启动脚本，该脚本会做几个事情：
    - 运行mysqld；
    - 运行监控程序，用于在mysqld挂掉时重启；
    - 重定向错误日志到日志文件。
  - 运行mysql.server：mysql.server也是一个启动脚本，调用mysqld_safe，调用方法为`mysql.server start` 和 `mysql.server stop`;
- Windows下如何启动MySQL服务器：
  - 将mysqld注册为服务：举例：`"C:\Program Files\MySQL\MySQL Server 5.7\bin\mysqld" --install`;
  - 启动和停止：`net start MySQL` 和 `net stop MySQL`
- 启动客户端程序：`mysql -h主机名  -u用户名 -p` 回车后输入密码；
- MySQL处理客户端请求的过程：连接-缓存-解析-优化-引擎。完成优化后，会生成执行计划，按照执行计划调用引擎API获取数据；
- innoDB的特点：支持事务（transactions）、分布式事务(XA)、行级锁(row-level locking)、外键(foreign keys)、部分事务回滚(SavePoints)；
- 指定表的存储引擎：
  - 创建表时指定：create table 表名（...）engine = 引擎名；
  - 修改表的引擎：alter table 表名 engine = 引擎名；

## 设置系统变量

### 命令行

在通过命令行启动程序时，可以通过命令行参数方式指定配置参数，此种方式只对当次启动有效

例如：

```shell
mysql -u root # 短选项方式指定用户名
mysql --user=root # 长选项方式指定用户名(注意：长选项方式等号两边不能有空格)
mysqld --default-storage-engine=MyISAM #长选项方式指定默认存储引擎
```

### 配置文件

MySQL会按照顺序从如下目录读取配置文件，并以最后一个为准，配置文件名称通常为my.ini或my.cnf，功能完全一样。

#### windows

- C:\windows\my.ini
- C:\my.ini
- mysql安装目录\my.ini
- 通过命令行参数--defaults-extra-file指定位置，例如：`mysqld --defaults-extra-file=C:\Users\my_extra_file.ini`

#### linux

- /etc/my.cnf

- /etc/mysql/my.cnf

- 编译安装目录/my.cnf ，在自己编译mysql安装时使用

- $MYSQL_HOME$/my.cnf 环境变量路径下，只用于服务器端

- defaults-extra-file 命令行指定配置文件路径

- ~/.my.cnf 用户目录下配置

  > 说明：如果没有配置环境变量，mysqld_safe程序会自动将环境变量置为mysql安装目录，因此默认会使用mysql安装目录下的配置文件；使用mysql.server启动会自动调用mysqld_safe程序；

#### 配置文件内容

- mysql配置文件的内容分为很多个组，例如： `server`、 `mysqld` `、mysqld_safe `、 `client` 、`mysql`、`mysqladmin`等；

- 配置项格式为：`option = value` ，等号两边可以有空格；

- server组配置项用于所有服务器端程序，如：`server`、 `mysqld` `、mysqld_safe `；

- client组配置项则用于客户端程序，如：`mysql`、`mysqladmin`、`mysqldump`；

- 在同一个配置文件中，配置按照从上到下的顺序配置，如果有相同功能的参数，以最后一个为准；
- 还可以通过`defaults-file`命令行参数指定配置路径，如果找不到会直接报错，而不会去扫描以上目录；

### 运行时修改变量

运行时可以通过客户端修改系统变量，此时系统变量分为**GLOBAL** 和 **SESSION**,即全局变量和会话变量；

例如：

```shell
# 设置全局变量，默认存储引擎为MyISAM,两种写法都可以
set global default_storage_engine = MyISAM; 
set @@global.default_storage_engine = MyISAM;
# 设置会话变量，不标明global还是session则默认为session
set session default_storage_engine = MyISAM; 
set @@session.default_storage_engine = MyISAM;
set default_storage_engine = MyISAM; 
```

需要注意，如果修改了global的系统变量，当前session变量不会跟着变，后续的session才会跟global一致；

### 状态变量

状态变量用于显示服务器当前状态，例如：

```shell
show status like 'thread%';
```



## MySQL中字符集

- 通常意义上的UTF-8字符集表示1个字符需要1~4个字节，常用字符通常需要1~3个字节，一些特殊字符例如emoji则需要4个字节来存储；

- mysql中的utf8字符集又叫utf8mb3,是阉割版的utf8字符集，用1~3个字节表示一个字符，用于常用字符；如果需要存储emoji表情之类的特殊字符，则需要使用utf8mb4字符集，这是完整的utf8字符集；

- 通常使用utf8字符集，即utf8mb3字符集，搭配utf8_general_ci比较规则，这个比较规则忽略大小写；

- 比较规则对照表：

  |  后缀  |       英文释义       |       描述       |
  | :----: | :------------------: | :--------------: |
  | `_ai`  | `accent insensitive` |    不区分重音    |
  | `_as`  |  `accent sensitive`  |     区分重音     |
  | `_ci`  |  `case insensitive`  |   不区分大小写   |
  | `_cs`  |   `case sensitive`   |    区分大小写    |
  | `_bin` |       `binary`       | 以二进制方式比较 |

- mysql中字符集和比较规则分为4个级别：服务器级别、数据库级别、表级别、列级别。

  - 服务器级别：通过系统变量指定字符集和比较规则：`character_set_server` 和 `collation_server`

  - 数据库级别：在创建数据库时指定，语法为：

    ```sql
    create database 数据库名 character set 字符集名 collate 比较规则名;
    ```

  - 表级别：在建表时指定，语法为：

    ```sql
    create table 表名 (...) character set 字符集名 collate 比较规则名;
    ```

  - 列级别： 在建表时指定每个字段的字符集和比较规则，语法为：

    ```sql
    create table 表名 (
    列名1 类型 character set 字符集名 collate 比较规则名,
    列名2 ...
    );
    ```

  - 如果没有指定，则会默认继承上一级的字符集和比较规则；