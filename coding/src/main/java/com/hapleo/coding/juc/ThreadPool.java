package com.hapleo.coding.juc;

import java.util.concurrent.Executors;

/**
 * 线程池的演示
 *
 * @author wuyulin
 * @date 2020/12/18
 */
public class ThreadPool {

    public static void main(String[] args) {

        Executors.newSingleThreadExecutor();
        Executors.newFixedThreadPool(5);
        Executors.newCachedThreadPool();
        Executors.newScheduledThreadPool(5);
        Executors.newSingleThreadScheduledExecutor();


    }
}
