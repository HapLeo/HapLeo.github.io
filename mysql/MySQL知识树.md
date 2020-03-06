# MySQL知识树

[TOC]

## MySQL

### 模块：连接器-缓存器-分析器-优化器-执行器-存储引擎

### 索引

- 索引结构
- 索引优化

### 事务

- 原子性：`undo log`
- 一致性：唯一性约束、类型约束等
- 隔离性：MVCC（基于undo log）
- 持久性：`redo log`

### 锁

####  锁的目的：保证隔离性（避免`脏写`、`脏读`、`不可重复度`、`幻读`）



####  锁的类型

- 共享锁（S锁）
- 排它锁（X锁、独占锁）
- 意向锁（I锁）
- 元数据锁（Matadata Locks/MDL）



#### 锁的粒度

- 表级锁
   - 表共享锁
   - 表排它锁
     	- 表意向锁
- 行级锁
   - 行共享锁
   - 行排它锁

- 间隙锁（仅用于防止**幻读**）
   - gap 锁
   - next-key 锁



#### 加锁时机

   - 行级共享锁：`lock in share mode` 主动加锁；阻塞写事务；
   - 行级排它锁：`for update` 主动加锁；阻塞读写事务；写操作（delete,update）
   - 表级共享锁：`lock tables t read` 主动加锁(innoDB中基本不用);
   - 表级排它锁：`lock tables t write` 主动加锁(innoDB中基本不用);
   - 意向共享锁：`lock in share mode`主动加锁后会在表上加IS锁（在表上做标记，表示存在加S锁的记录，避免遍历）；
   - 意向独占锁：`for update` 主动加锁或delete/update操作会在表上加IX锁（在表上做标记，表示存在加X锁的记录，避免遍历）;
   - 元数据锁：`DDL`与`select/insert/delete/update`并发执行时，由server层加锁；



#### 锁保证隔离性原理

- 脏写：一个事务修改了另一个事务未提交的数据为脏写，写操作加X锁后，其他事务阻塞写操作来避免脏写；

- 脏读：一个事务读到了另一个事务未提交的数据为脏读，读操作加S锁，写操作加X锁，相互阻塞避免脏读；

  	- 不可重复读：一个事务连续读两次，数据不一致，说明中间被其他事务修改过，读操作加S锁可阻塞其他事务写操作避免不可重复读；

  	- 幻读：一个事务读到其他事物新增且未提交的数据为幻读，加gap锁或next-key 锁可阻塞其他事物插入数据避免幻读(等值查询加gap锁，范围查询为范围内所有记录加next-key锁)；



#### 死锁场景：互相等待对方持有的锁

   - 场景一：A事务需要先获取二级索引中某条记录的锁，再获取聚簇索引中的锁，同时B事务相反时，产生死锁；

        如下语句先取二级索引锁再取聚簇索引锁：

```sql
// col_name_1 字段存在二级索引时，如下语句会先获取二级索引记录锁，再获取聚簇索引记录锁
select * from table_name where col_name_1 = 'aaa' lock in share mode; 
select * from table_name where col_name_1 = 'aaa' for update;
update table_name set col_name_2 = 'bbb' where col_name_1 = 'aaa'; 
delete from table_name where col_name_1 = 'aaa';
```

​		如下语句先取聚簇索引锁再取二级索引锁：
```sql
// pri_key 字段存在二级索引时，如下语句会先取聚簇索引锁再取二级索引锁
 select * from table_name where pri_key = 1 lock in share mode; 
 select * from table_name where pri_key = 1 for update;
 update table_name set col_name_1 = 'bbb' where pri_key = 1; 
 delete from table_name where pri_key = 1;  
```

- 场景二：待补充

### SQL优化
- 单表查询优化：索引
- `join`关联查询优化：索引
- `order by`、`group by` 优化：索引
- `explain`：执行计划
- `optimizer trace`: 优化跟踪

