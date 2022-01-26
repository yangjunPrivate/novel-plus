package com.java2nb.novel.handler;

/**
 * @Author Jun Yang
 * @Date 2022/1/26 10:28 上午
 * @Version 1.0
 */
public interface CrawlerUpdate {

    Integer sourceId();

    void doUpdate(String bookId);
}
