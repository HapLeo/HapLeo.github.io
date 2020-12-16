package com.hapleo.coding.argulrithm;

/**
 * 反转链表
 *
 * @author wuyulin
 * @date 2020/12/16
 */
public class ListRevert {


    /**
     * 方法一：递归法
     * 寻找到最后一个节点，将最后节点的指针转换，返回最后节点
     * 思路要点：
     * 1. 结束条件：节点遍历结束时
     * 2. 结束条件中需要做什么？ 返回最后一个节点
     * 3. 递归方法后面要执行什么？ 反转指针，并将最后一个节点传递下去直到最终返回
     *
     * @param head
     * @return
     */
    public ListNode reverseList(ListNode head) {

        return revert(null, head);
    }

    public ListNode revert(ListNode cur, ListNode pre) {
        if (pre == null) {
            return cur;
        }
        ListNode lastNode = revert(pre, pre.next);
        pre.next = cur;
        return lastNode;
    }


    /**
     * 方法二：双指针法
     * 要点：不要改变传入的参数，否则会思维混乱
     * 双指针一起向前走，边走边翻转指针
     * 不要丢掉尚未翻转的引用，使用临时引用暂存，指针反转后交还
     *
     * @param head
     * @return
     */
    public ListNode reverseList2(ListNode head) {

        // 当前节点相当于最终的尾部
        ListNode cur = null;
        ListNode pre = head;

        while (pre != null) {
            // 暂存pre节点的后面节点
            ListNode temp = pre.next;
            // 将pre节点指针指向当前节点
            pre.next = cur;
            // 当前节点前移
            cur = pre;
            // pre节点后移
            pre = temp;
        }

        // 最后当pre节点为空时跳出循环，返回cur节点
        return cur;
    }


    class ListNode {

        private int value;

        private ListNode next;
    }

}
