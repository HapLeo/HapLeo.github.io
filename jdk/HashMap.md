# HashMap实现原理分析

## 1. HashMap的存储结构是什么?

HashMap是通过在数组中保存单向链表或红黑树来实现的。HashMap通过调用Key的hashCode方法，获取一个int类型的hash值，再与存放记录的数组的长度做按位与运算，计算出该对象落在哪个数组下标，如果该下表已有值，则形成单向链表。在JDK1.8+中，当单向链表的长度大于等于8时，变换成红黑树，以加快查找速度。

## 2. HashMap中的数组是如何扩容的？
HashMap中定义了如下属性：

```java
Node<K,V>[] table; // 存放键值对的数组
int size; // 键值对的数量
final DEFAULT_INITIAL_CAPACITY = 16; // 默认初始化容量
final float DEFAULT_LOAD_FACTOR = 0.75; // 默认加载因子，用于计算扩容阈值
final int MAXIMUM_CAPACITY = 1 << 30; // 最大容量，二的三十次方
int threshold；// 扩容的阈值，达到该容量就进行扩容 
```

扩容过程如下:

1. 第一次put进元素时，table=null,将其初始化成DEFAULT_INITIAL_CAPACITY长度的数组；

2. 计算下一次扩容的阈值threshold = 扩容后数组长度 × DEFAULT_LOAD_FACTORY;
3. 此后每次put操作结束都检查键值对个数size是否>threshold
4. 如果键值对个数大于threshold，则重新创建一个两倍容量的数组，threshold也扩至两倍，并``将键值对复制到新数组中``；
5. 当容量>=MAXMUM_CAPACITY,就不再扩容，直接返回原始数组；

所以理论上，再未达到最大容量之前，最好的情况是每个数组位置只保存一个键值对，并且一定会有多于四分之一的空闲空间。此时查询速度最快，时间复杂度为O(1);
但是再好的hash算法也无法完全避免hash冲突。

## 3. HashMap是如何处理hash冲突的？

```java
public V put(K key,V value){
	return putVal(hash(key),key,value,false,true);
}

/**
* @Param hash 通过调用key的hash方法获得的int值，与table长度进行按位与运算可以获得指定下标
*
*/
final V putVal(int hash,K key,V value,boolean onlyIfAbsent,boolean evict)
{
	if(table == null){
		// 第一次put键值对，初始化table对象，并讲键值对放入指定下标位置
	}
	else if(table[index] == null){
		// 这里为了便于理解，写了伪代码，如果指定下标还没有存储任何值，就直接存在这里

	}
	else{
		// 指定下标已经存在键值对,将新节点加到链表尾部
		// 判断该下标保存的键值对链表长度是否小于8，如果不小于8，变成红黑树结构
	}
}

```
2. 获取数组下标：put方法接受一个键值对，通过调用key的hashCode方法获取hash值，这个hash值是int值，int类型是4个字节，即32位，通过按位右移16位（hash>>>16）获取高32位的int值，保存时通过与数组长度按位与得到要保存到的数组下标。
2. 单链表解决hash冲突：如果hash出的数组下标已经有值，则将新的键值加到键值对尾部形成单链表
3. 如果该下标的单链表长度不小于8，则变换结构成红黑树以加速查询效率（红黑树查询的时间复杂度为O(logn)）


## 4. 数组扩容时是如何重新存放键值对的？

HashMap中的扩容方法为reset()。
该方法遍历键值对数组，取出每个数组下标对应的链表的头结点。
按照常规做法，我们会遍历每个链表结点，重新计算hash值并获取到新的数组下标，但HashMap设计者在这里做了一个精心的设计。
他将数组大小规定为2的4次方开始，每次按位左移一位（即乘2）作为新的数组大小,假设数组长度为16，即2的4次方（n = 4），则数组下标的二进制就是hashCode的后n位。
这样巧妙地将数组下标与hashCode做了关联，在扩容的时候，因为容量扩大成2倍,因此只需要查看链表中每个元素的hash值的第n+1位是0还是1即可。
如果是0则保持在当前位置不变，如果是1则移动到（当前位置+扩展数量）位置，因为它的hashCode二进制刚好加了了2的n+1次方。
举例如下：
initCapacity = 2 << 4 = 16,binary(16-1) = 1111;
一个对象的hashCode=1011001001，与1111按位与得到1001，即hashCode后四位，转换成10机制就是它的数组下标9；
扩容时，只需获取hashcode的前一位，这里是0，就直接可以推断出下标仍是9，因为0&1=0，结果不变。
如果hashCode的前一位是1，则新下标=11001=（1*2^4）+ （0*2^3）+ (0*2^2) + (0*2^1) + (1*2^0) = 16+9=25;
这样做就不需要通过hashCode方法进行重新计算了。
这就是数组长度定为2的n次方的原因，大概也是作者所说的“power of two”。

## 5. 为什么常用String对象作为HashMap的key？

1. String类重写了hashCode和equals方法，由内存地址生成hashcode变成由字段值生成hashcode，equals方法也重写成判定字段值。这样，只要key值相等，就可以获得相应的value，不需原对象作为key来取值，使用更方便。
2. String类中用于保存值的字段通过final修饰，因此可以保证在传递引用的过程中不会被修改，适合作为key类型使用。	