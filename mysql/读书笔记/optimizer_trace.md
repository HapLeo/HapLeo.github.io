**optimizer_trace**: optimizer-优化器，trace-跟踪，用于查看优化器生成执行计划的过程。

explain 命令得到的是优化器最终选择的执行计划，而optimizer_trace则记录了整个选择的过程，让你知道为什么选择了这个执行计划。

查看是否开启该功能：

```sql
show variables like 'optimizer_trace';
```

开启该功能：

```sql
set optimizer_trace = 'enabled=on';
```

开启功能后，执行sql语句时会在information_schema.optimizer_trace表中生成跟踪日志。

查看跟踪日志：

```sql
select * from information_schema.optimizer_trace;
```

**join_preparation**:  准备阶段
**join_optimization**: 优化器优化阶段
**rows_estimation**：估算单表访问的不同方法的成本
**join_execution**：执行阶段
**considered_execution_plans**：分析各种执行计划