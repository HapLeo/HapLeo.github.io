什么是Buffer Pool?

**Buffer Pool**：译为`缓冲池`，用于缓存innoDB的索引页的内存空间。因为innoDB读取数据每次读取一页，为避免频繁I/O而建立的缓存池。

缓存池大小默认128M，可以通过配置文件修改缓存池大小：

```shell
[server]
innodb_buffer_pool_size = 指定字节数
```

当需要缓存一个页时，应该往哪儿存？

**free链表**：Buffer Pool的内存在初始化时分割成大小为16KB的缓存页，空闲缓存页形成双向链表等待缓存数据，需要缓存时，获取链表头结点。

如何判断一个页在Buffer Pool中是否存在？

通过hash表定位。表空间号+页号作为key，页内容作为值。

**脏页**： Buffer Pool中缓存页的数据被修改，与磁盘不一致，那么这个页叫做脏页。

**flush链表**：被修改过的页连成双向链表，等待被刷入磁盘，这个链表叫做flush链表。

Buffer Pool被存满了怎么办？

**LRU链表**：缓存页再形成一条链表，每次将访问的缓存页移到头部，尾部就是最近最少使用的缓存页。LRU(Least Recently Used)。

**预读**：如果缓存了某个区的13个页面，会触发整个区的缓存。如果顺序访问了某个区的56个页面，会缓存整个下一个区。这些缓存页同样会放到LRU链表头部。

**全表扫描**： 如果一个表中记录非常多，在执行一次全表扫描后，这个表的所有页面都被缓存到LRU链表，这可能会导致LRU链表尾部大量淘汰。如果这种全表扫描频率不高，则会大大降低缓存命中率。

如何解决预读和全表扫描导致的缓存淘汰问题？

**LRU链表分区**：按照某个比例，LRU链表前半段为热区或young区，后半段为冷区或old区。比例通过系统变量值`innodb_old_blocks_pct`指定，默认值为37，即old区占LRU链表的37%；

链表分区后，首次访问的页会被放到old区头部，并且超过指定时间间隔再次被访问才能移动到young区头部。时间间隔通过系统变量`innodb_old_blocks_time`指定，默认值为1000，单位毫秒。

如何解决LRU链表频繁移动节点的消耗问题？

如果访问的页在young区前1/4部分，则不移动，以减少消耗。

如何提高Buffer Pool读写效率？

**buffer pool多实例**：可以配置多个buffer pool实例，共享`innodb_buffer_pool_size`空间：

```shell
[server]
innodb_buffer_pool_instances = 2
```

如何查看Buffer Pool目前的状态？

通过如下语句查看innoDB引擎的状态，包括buffer pool的状态信息：

```sql
SHOW ENGINE INNODB STATUS;
```







