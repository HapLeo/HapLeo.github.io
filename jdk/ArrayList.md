# ArrayList实现原理分析

## 1. 讲一讲ArrayList?

没有定向的提问，就抓住它的特征来讲。

- ArrayList是通过数组实现的，因此具有数组的基本特征：支持随机访问，但插入和删除需要数据搬移。
- ArrayList实现了一个自动扩容的数组，因此不必数组那样指定大小，当空间不够时，会自动扩容为1.5倍。
- 非线程安全：因为ArrayList类中，add方法不是原子操作，其包含了数组大小值的累加和扩容操作，因此不是线程安全的类。对应线程安全的类是java.util.concurrent包下的CopyOnWriteArrayList。 

## 2. 如何实现一个ArrayList?
- 思路：

  > 定义一个数组字段elementData，用于保存元素；
  > 每次add方法会检测当前剩余空间，如果没有剩余空间，进行扩容；
  > 放入第一个值时，重新构造一个数组，大小为默认大小10。
  > 扩容时，使用浅拷贝方式，即只拷贝引用，不拷贝值。
  > 插入和删除数据时，需要进行数据搬移操作。

- 几个细节：

1. 为什么用于保存对象的数组elementData字段被生命成了transient？

> transient关键字修饰的字段在使用JVM默认序列化时会被忽略，常用于忽略敏感信息或无用信息。但ArrayList中的最重要的部分就是elementData字段，这里用transient是因为JVM默认序列化方式会将
> 集合中尚未使用到的数组位置也序列化出来。因此，ArrayList补充了writeObject和readObject方法，并在writeObject中手动实现了elementData字段的序列化工作。
> 当JVM看到这两个方法时，会自动调用，从而避免将未使用的数组空间序列化。

3. ArrayList中为什么要定义两个空数组属性(DEFAULT_CAPACITY_EMPTY_ARRAY和EMPTY_ARRAY)？

> 用于进行不同的扩容方式。
> 我们在创建一个ArrayList时，通常有两种构造方式：new ArrayList()和new ArrayList(int n);
> 无参构造方法会将elementData赋值成DEFAULT_CAPACITY_EMPTY_ARRAY，首次扩容时容量会变成DEFAULT_CAPACITY的值10，此后1.5倍扩容；
> 有参构造方法分两种，n=0和n>0;
> n=0,elementData被赋值为EMPTY_ARRAY，首次扩容时容量会变成1，此后1.5倍扩容；
> n>0,elementData被直接赋值为指定大小的数组Object[n],此后扩容也是从n开始进行1.5倍扩容；
> 为了辨别初始化时赋值的空数组是默认的还是指定的n=0，以进行不同的扩容方式，因此定义了这两个空数组；