package com.hapleo.coding.argulrithm.sort;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * 冒泡排序测试
 *
 * @author wuyulin
 * @date 2020/12/17
 */
class BubboSortTest {


    @Test
    void sort1() {

        int[] arr = new int[10000];
        for (int i = 0; i < 10000; i++) {
            arr[i] = (int) (Math.random() * 10000);
        }

        //System.out.println(Arrays.toString(arr));
        BubboSort.sort1(arr);
        System.out.println(Arrays.toString(arr));
    }
}