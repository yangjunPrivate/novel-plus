package com.java2nb.novel.handler;

import cn.hutool.core.collection.CollectionUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author Jun Yang
 * @Date 2022/1/26 10:08 上午
 * @Version 1.0
 */
@Component
public class CrawlerHandlerSupport {

    Map<Integer,Crawler> crawlerMap = new HashMap<>();

    public CrawlerHandlerSupport(ObjectProvider<List<Crawler>> listObjectProvider){
        if(listObjectProvider == null || CollectionUtil.isEmpty(listObjectProvider.getIfAvailable())){
            return;
        }
        listObjectProvider.getIfAvailable().stream().forEach(crawler -> {
            crawlerMap.putIfAbsent(crawler.sourceId(),crawler);
        });
    }

    public Crawler getCrawlerHandler(Integer sourceId){
        return crawlerMap.get(sourceId);
    }
}
