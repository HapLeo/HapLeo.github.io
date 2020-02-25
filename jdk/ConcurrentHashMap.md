# ConcurrentHashMap面试题

## 提出问题

1. ConcurrentHashMap是如何保证线程安全的？


2. 为什么ConcurrentHashMap的key和value都不能为null？

3. 

## 源码解析

来一段put方法的源码：

```java
// 这是put方法，调用了putVal方法
    public V put(K key, V value) {
        return putVal(key, value, false);
    }

// 这是put方法的具体实现
    /** Implementation for put and putIfAbsent */
    final V putVal(K key, V value, boolean onlyIfAbsent) {

	// 如果key为空或者value为空，都抛出空指针异常
        if (key == null || value == null) throw new NullPointerException();
	// 通过key计算hashcode，spread方法只是做了简单的加工，使得hashcode分布更均匀 
        int hash = spread(key.hashCode());
        int binCount = 0;
	// 循环键值对数组中的键值对元素
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh; K fk; V fv;
            if (tab == null || (n = tab.length) == 0)
		// 第一次调用，初始化键值对数组
                tab = initTable();
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
		// 如果hash到的数组下标位置为空，就调用casTabAt这个神奇的无锁方法将新生成的Node值放入i这个位置，并跳出循环
                if (casTabAt(tab, i, null, new Node<K,V>(hash, key, value)))
                    break;                   // no lock when adding to empty bin
            }
		// 如果下标位置已经有元素了，获取头结点的hash值
		// 几种状态码的含义:
		// MOVED: 
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            else if (onlyIfAbsent // check first node without acquiring lock
                     && fh == hash
                     && ((fk = f.key) == key || (fk != null && key.equals(fk)))
                     && (fv = f.val) != null)
                return fv;
            else {
                V oldVal = null;
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K,V> e = f;; ++binCount) {
                                K ek;
                                if (e.hash == hash &&
                                    ((ek = e.key) == key ||
                                     (ek != null && key.equals(ek)))) {
                                    oldVal = e.val;
                                    if (!onlyIfAbsent)
                                        e.val = value;
                                    break;
                                }
                                Node<K,V> pred = e;
                                if ((e = e.next) == null) {
                                    pred.next = new Node<K,V>(hash, key, value);
                                    break;
                                }
                            }
                        }
                        else if (f instanceof TreeBin) {
                            Node<K,V> p;
                            binCount = 2;
                            if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key,
                                                           value)) != null) {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                    p.val = value;
                            }
                        }
                        else if (f instanceof ReservationNode)
                            throw new IllegalStateException("Recursive update");
                    }
                }
                if (binCount != 0) {
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
        addCount(1L, binCount);
        return null;
    }

```
