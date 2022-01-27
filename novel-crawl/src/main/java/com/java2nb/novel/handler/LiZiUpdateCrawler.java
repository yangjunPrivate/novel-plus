package com.java2nb.novel.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.java2nb.novel.core.constants.CrawlerConstants;
import com.java2nb.novel.core.utils.StringUtil;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookCategory;
import com.java2nb.novel.entity.BookContent;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Author Jun Yang
 * @Date 2022/1/26 11:22 上午
 * @Version 1.0
 */
@Component
@Slf4j
public class LiZiUpdateCrawler extends BaseCrawlerUpdate {

    @Value("${li.zi.crawler.host:http://localhost:8089/commonEmpower/}")
    String host;
    String PARTNER_ID = "partnerId=168";

    String BOOK_ID  = "&bookId=";
    String CHAPTER_ID  = "&chapterId=";

    @Override
    public BookIndex buildBookIndex(Map<String, Object> bookIndexMap, Book book) {
        BookIndex bookIndex = new BookIndex();
        long l = idWorker.nextId();
        bookIndex.setId(l);
        bookIndex.setBookId(book.getId());
        bookIndex.setIndexName(MapUtils.getString(bookIndexMap,"chapterName"));
        bookIndex.setCreateTime(new Date());
        bookIndex.setIndexNum(MapUtils.getInteger(bookIndexMap,"chapterId"));
        bookIndex.setIsVip(MapUtils.getByte(bookIndexMap,"isFree"));
        String updateTime = MapUtils.getString(bookIndexMap, "updateTime");
        bookIndex.setUpdateTime(DateUtil.parse(updateTime,"YYYY-MM-dd hh:mm:ss"));
        return bookIndex;
    }

    @Override
    public BookContent buildBookContent(BookIndex bookIndex, Book book, String content) {
        if(StringUtils.isBlank(content)){
            return null;
        }
        BookContent bookContent = new BookContent();
        int wordCount = StringUtil.getStrValidWordCount(content);
        bookIndex.setWordCount(wordCount);
        bookContent.setContent(content);
        bookContent.setIndexId(bookIndex.getId());
        return bookContent;
    }

    @Override
    public List<Map> formatBookIndex(String bookIndexStr, Book book) {
        JSONObject data = JSONUtil.parseObj(bookIndexStr).getJSONObject("data");
        if(data.isEmpty()){
            return Collections.emptyList();
        }
        return JSONUtil.toList(data.getJSONArray("list"),Map.class);
    }

    @Override
    public Book formatBook(String bookId, String bookDetailStr) {
        JSONObject jsonObject = JSONUtil.parseObj(bookDetailStr);
        Book book = new Book();
        book.setBookName(jsonObject.getStr("bookName"));
        book.setAuthorName(jsonObject.getStr("authorName"));
        book.setAuthorName(jsonObject.getStr("authorName"));
        book.setPicUrl(jsonObject.getStr("imgPath"));
        book.setBookDesc(jsonObject.getStr("intro"));
        //都没有，设置成固定值
        book.setVisitCount(Constants.VISIT_COUNT_DEFAULT);
        book.setScore(6.5f);
        String bookTypeId = jsonObject.getStr("bookTypeId", "0");
        BookCategory category = getCrawlerBookCategory(bookTypeId);
        book.setWorkDirection(category.getWorkDirection());
        book.setCatId(category.getId());
        book.setCatName(category.getName());

        Integer status = jsonObject.getInt("status", 0);
        Byte crawlerBookStatus = getCrawlerBookStatus(status + "");
        book.setBookStatus(crawlerBookStatus);
        book.setCrawlSourceId(sourceId());
        book.setCrawlBookId(bookId);
        book.setCrawlLastTime(new Date());
        return book;
    }


    @Override
    public Integer sourceId() {
        return CrawlerConstants.SOURCE_ID_LI_ZI;
    }

    @Override
    public String getBookDetailUrl(String bookId) {
        return host+"bookInfo?"+PARTNER_ID+""+BOOK_ID+bookId;
    }
    @Override
    public String getChapterListUrl(String bookId) {
        return host+"chapters?"+PARTNER_ID+BOOK_ID+bookId;
    }
    @Override
    public String getChapterContent(String bookId, String chapterId) {
        return host+"chapterInfo?"+PARTNER_ID+BOOK_ID+bookId+CHAPTER_ID+chapterId;
    }

}
