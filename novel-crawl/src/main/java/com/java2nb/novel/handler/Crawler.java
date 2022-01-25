package com.java2nb.novel.handler;

/**
 * @Author Jun Yang
 * @Date 2022/1/25 3:55 下午
 * @Version 1.0
 */
public interface Crawler {

    /**
     * 渠道
     * @return
     */
    Integer sourceId();

    /**
     * 抓取
     */
    void crawl();
}
