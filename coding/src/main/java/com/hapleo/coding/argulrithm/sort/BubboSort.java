package com.hapleo.coding.argulrithm.sort;

/**
 * 冒泡排序
 *
 * @author wuyulin
 * @date 2020/12/17
 */
public class BubboSort {

    /**
     * 冒泡排序的基本实现
     * 思路：相邻的元素两两交换，每一轮结束，就会将最大值交换到尾部
     *
     * @param arr
     * @return
     */
    public static int[] sort1(int[] arr) {

        for (int i = 0; i < arr.length - 1; i++) {
            for (int j = 0; j < arr.length - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    int temp = arr[j + 1];
                    arr[j + 1] = arr[j];
                    arr[j] = temp;
                }
            }
        }

        return arr;
    }
}
