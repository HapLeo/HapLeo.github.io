# CopyOnWriteArrayList类思考

## 1. 什么是CopyOnWrite?

CopyOnWrite通常翻译成"写时复制",即向容器写入数据时,先复制出一个容器的副本进行写入,写入完成后再赋值给原先的引用。这么做的目的是进行"读写分离",写操作不影响读操作,以提高读操作性能.
下面这段代码,是CopyOnWriteArrayList的add方法实现,即实现了CopyOnWrite思想.
```java

    // add方法加同步锁
    public boolean add(E e) {
	// 加同步锁
        synchronized (lock) {
            Object[] es = getArray();
            int len = es.length;
		// 复制出新数组
            es = Arrays.copyOf(es, len + 1);
		// 新数组中放入新元素
            es[len] = e;
		// 将新数组赋值到原引用
            setArray(es);
            return true;
        }
    }

    // get方法无需加同步锁,提高了并发读操作性能
    public E get(int index) {
        return elementAt(getArray(), index);
    }

```

## 2. CopyOnWrite的实现为什么比直接使用synchronize效率更高?

CopyOnWrite实现效率能够提高,是因为写操作不会阻塞读操作,从而提高并发读性能.加入不使用CopyOnWrite思想,那么写操作与读操作就必须加同一把锁,即读和写不能同时进行,一旦同时进行,
则会出现读到写的中间状态的情况.

## 3. 为什么不使用读写锁来实现,而要使用CopyOnWrite?

读写锁在没有任何线程写的情况下,是可以并发读的,但是一旦存在一个线程执行写操作,就无法进行并发的读操作,即只有读-读操作支持并发,读-写操作和写-写操作都是同步的.
而CopyOnWriteArrayList的读方法不加任何锁,即使有其他线程正在进行写操作,也不会阻塞读操作,因此会比读写锁的实现更快,更适合高并发环境.

## 4. CopyOnWriteArrayList适用于什么场景?

CopyOnWrite思想思想的实现都适用于"读多写少"的场景.因为写操作是同步的,因此并发写性能较低.此外,每次写操作都要创建一个副本,对于内存和GC都带来了压力.
还有一点,CopyOnWrite无法提供强一致性,因为读操作并未加同步锁,当并发执行读写操作时,在写操作将引用指向新数组之前,读操作都在读未进行写操作的那个数组.
这就是CopyOnWrite的意义,既保证了并发读写,又不会读到写操作的中间状态,只是牺牲了强一致性,换来了读操作的效率.


## 5. CopyOnWriteArrayList有类似于ArrayList的扩容机制吗?
没有.因为CopyOnWriteArrayList每次写操作都要拷贝一个新的数组并重新为数组对象的引用赋值,因此每次写操作只需将新数组的长度定为(原数组长度+新元素个数)即可,无需自动扩容.




