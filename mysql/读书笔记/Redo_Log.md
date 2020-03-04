# redo log 读书笔记

**什么是`redo log`？**

**redo log**：译为`重做日志`， 是一种存放在磁盘上的日志，记录了buffer pool 中页的修改历史，用于数据的崩溃恢复。

**为什么需要redo log ?**

因为buffer pool 缓存了B+树的页，而每次修改数据都是先写到buffer pool的页中，此时并不会立刻刷脏页。如果此时系统崩溃，则会导致buffer pool中尚未刷入磁盘的数据丢失。为此，加入了redo log，每次修改buffer pool中的页就会写redo log，系统崩溃后恢复redo log即可。

**为什么不直接刷脏页呢？**

主要是I/O效率问题： 

1. `整页存取`:innoDB读写数据都是以页为单位，每次修改都向磁盘写16KB数据会造成巨大的性能损耗。
2. `随机I/O`:脏页之间并不一定是连续的磁盘空间，随机I/O效率很低。

**redo log 为什么能解决这些问题？**

1. redo log 格式精简；
2. redo log 顺序I/O；

redo log 大概是什么样子？

| type     | space ID | page number | data         |
| -------- | -------- | ----------- | ------------ |
| 日志类型 | 表空间ID | 页号        | 本页修改内容 |

为了减小redo log的空间占用，设计者将redo log分为很多种类型，每种类型存储的内容各不相同，有一些会采用压缩算法，目的都是在保证崩溃恢复机制下尽量减少I/O。 

**是一个事务生成一条redo log 吗？**

不是。一个事务可能包含多条语句，每条语句可能会修改多个页、多个索引、导致页分裂等。因此，将一个事务中每个语句的执行过程拆分成多个`Mini-Transaction`,简写为`mtr`,每个mtr对应一组不可分割的redo log。将日志写入磁盘时，会将一个mtr生成的一组redo log 完整的写入日志文件，这一组redo log 用`MLOG_MULTI_REC_END`类型的redo log来标记结束。

**redo log 如何写入磁盘？**

redo log也不是直接写入磁盘，而是先保存在redo log block，这是redo log的页，大小为512字节。redo log block保存在redo log buffer中，这块内存是redo log的缓冲区，redo log block 写入redo log buffer是顺序写入的。

**如何记录redo log的生成顺序？**

**LSN (Log Sequence Number)**: 简写为`lsn`,译为`日志序列号`，用来记录redo log的日志量和日志顺序，是全局变量，lsn值越小，说明redo log 产生的越早。lsn初始值为8704，当redo log 存入 redo log block时，会将占用的字节数累加到lsn值上，每个redo log block 的header和trailer分别占用12个字节和4个字节，也要累加到lsn值上。比如第一个mtr对应的redo log 存入 redo log block 时，假设redo log占用100字节，则lsn的值会从初始值8704变为`8704 + 12(block header size) + 100(redo log size) = 8816`,因为没有超过第一个block的大小即512字节，所以不用累加block trailer的4个字节。如果继续存入500个字节的redo log，则会从`8816` 变为 `8816 + 412（第一个block剩余空间）+ 4（第一个block的trailer size）+ 88（剩余的redo log） =  9320`。 

**什么时候将redo log buffer中的redo log写入磁盘？**

1. 事务提交时。事务提交时，可以先不刷脏页，但为了保证持久性，会将redo log写入磁盘；也可通过系统变量`innodb_flush_log_at_trx_commit`修改；
2. 后台线程：后台存在专门负责将redo log写入磁盘的线程；
3. 关闭服务时：正常关闭服务前会自动写入磁盘；

**redo log 是哪个文件？**

redo log 默认保存在`mysql数据目录`下名为`ib_logfile0`和`ib_logfile1`两个文件，轮换写入，即`ib_logfile0`写满了就写`ib_logfile1`，`ib_logfile1`写满了又回到`ib_logfile0`覆盖之前的日志。

**redo log 文件被轮换写入，如何防止日志对应的修改还没刷脏页就被覆盖？**

先思考一下，什么样的redo log 可以被覆盖呢?

对于log buffer来说，只要redo log 写入磁盘了，那块内存就可以被覆盖了。因此，在log buffer 中，buf_free指向空闲区域的起始内存地址，即log buffer 中redo log的结束点，buf_next_to_write 指向redo log的起始内存地址。同时，一个全局变量`flushed_to_disk_lsn`记录了最后一条写入磁盘的redo log对应的lsn值。如果lsn值与flushed_to_disk_lsn值相同，则说明所有redo log都写入了ib_logfile。

对于ib_logfile来说，如果redo log 对应到buffer pool中的脏页已经写到磁盘，则logfile上面的这条日志可以被覆盖，因此，ib_logfile也需要保存两个指针，一个指向空闲区域的起始磁盘地址，即崩溃恢复的结束点，另一个指向等待buffer pool中刷脏页的位置，即崩溃恢复的起始点。

崩溃恢复的起始点保存在ib_logfile文件的头部结构中。

**checkpoint**：每个ib_logfile的开始2048个字节存储了这个文件的概要信息，其中有一个checkpoint结构记录了redo log 崩溃恢复的起始点。

checkpoint结构：

| LOG_CHECKPOINT_NO                                            | LOG_CHECKPOINT_LSN          | LOG_CHECKPOINT_OFFSET                             | LOGCHECKPOINT_LOG_BUF_SIZE      | free | LOG_BLOCK_CHECKSUM |
| ------------------------------------------------------------ | --------------------------- | ------------------------------------------------- | ------------------------------- | ---- | ------------------ |
| checkpoint编号，每做一次checkpoint就加1，用于崩溃恢复时找到最新的日志文件 | LSN值，标记崩溃恢复的起始点 | LSN值偏移量，用于在文件中快速定位崩溃恢复的起始点 | 生成checkpoint时log buffer 大小 | 空闲 | 校验值             |

什么时候更新这个checkpoint_lsn值？

buffer pool 中的flush链表中每个页的控制块中，会记录两个lsn值来表示该页何时被修改：

`oldest_modification`: 一个页加载到buffer pool后的第一次修改结束后，记录对应的lsn值。

`newest_modification`: 一个页在buffer pool 中每次修改后，记录对应的lsn值，即记录最新修改的序列号。

一个页加载到buffer pool 后，第一次修改会将它加入flush链表，此后再修改并不会移动它在链表中的位置，因此，flush链表是按照`oldest_modification`的值从大到小连接的。而每次刷脏页则会从链尾开始，因此，当尾结点刷脏页之后，移除flush链表，此时flush链表的尾结点的oldest_modification的值就是未刷脏页的最早的节点,此时做一次`checkpoint`操作，即将这个oldest_modification值保存到ib_logfile头部的checkpoint结构log_checkpoint_lsn位置，表示所有小于这个值的redo log 都可以被覆盖。

如何确定崩溃恢复的终点？

redo log 在 redo log buffer 中是保存在 redo log block 结构中的，写入文件时也连带block的header、trailer结构一起写入了，header中`LOG_BLOCK_HDR_DATA_LEN`字段保存了这个block中存放的redo log的大小，填满block的大小为512字节，如果小于512字节，说明是崩溃恢复的最后一个block。

**redo log中大于log_checkpoint_lsn的日志对应的脏页也有可能已经被刷脏，如何避免崩溃恢复时做了多余的恢复？**

每一个页的file_header部分都保存了一个`FIL_PAGE_LSN`值，在flush链表尾结点刷脏页并做checkpoint时会将脏页的`newest_modification`值存入`FIL_PAGE_LSN`。在崩溃恢复时，如果这个值大于`LOG_CHECKPOINT_LSN`值，说明这个页面不需要崩溃恢复。

**redo log 崩溃恢复时是沿着日志记录顺序恢复的吗？**

不是。顺序恢复效率较慢，同一个页会读取多次。innoDB中将恢复起点到恢复终点的日志按照文件中的顺序存入哈希表，用`space_ID+page_number`作为key,redo log 作为值，相同的key通过链表连接，这样可以将一页的所有操作一起恢复。



