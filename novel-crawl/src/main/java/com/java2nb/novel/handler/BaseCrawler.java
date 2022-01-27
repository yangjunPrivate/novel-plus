package com.java2nb.novel.handler;

import cn.hutool.core.collection.CollectionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.constants.CrawlerConstants;
import com.java2nb.novel.core.crawl.RuleBean;
import com.java2nb.novel.core.utils.HttpUtil;
import com.java2nb.novel.entity.*;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.CrawlService;
import io.github.xxyopen.util.IdWorker;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.java2nb.novel.core.utils.HttpUtil.getByHttpClientWithChrome;

/**
 * @Author Jun Yang
 * @Date 2022/1/25 3:58 下午
 * @Version 1.0
 */
@Component
@Slf4j
public abstract class BaseCrawler implements Crawler{

    @Autowired
    BookService bookService;
    @Autowired
    CrawlService crawlService;

    public final IdWorker idWorker = IdWorker.INSTANCE;



    @Override
    public void crawl() {
        //获取列表
        String bookListUrl = getBookListUrl();
        String bookListHtml = HttpUtil.getByHttpClientWithChrome(bookListUrl);
        List<String> bookIdList = formatBookIdList(bookListHtml);
        if(CollectionUtils.isEmpty(bookIdList)){
            return;
        }
        //循环处理单本
        bookIdList.forEach(sourceBookId ->{
            //通过id获取明细
            String bookDetailUrl = getBookDetailUrl(sourceBookId);
            String bookDetailStr = HttpUtil.getByHttpClientWithChrome(bookDetailUrl);
            //构建book对象
            Book book = formatBook(sourceBookId, bookDetailStr);
            if (book.getBookName() == null || book.getAuthorName() == null) {
                return;
            }
            //这里只做新书入库，查询是否存在这本书
            Book existBook = bookService.queryBookByBookNameAndAuthorName(book.getBookName(), book.getAuthorName());
            //如果该小说不存在，则可以解析入库，但是标记该小说正在入库，30分钟之后才允许再次入库
            if (existBook == null) {
                //没有该书，可以入库
                //解析章节目录
                book.setId(idWorker.nextId());
                parseBookContent(sourceBookId,book);
            } else {
                //只更新书籍的爬虫相关字段
                bookService.updateCrawlProperties(existBook.getId(), sourceId(), sourceBookId);
            }

        });




    }


    public boolean parseBookContent(String sourceBookId,Book book){
        //获取目录列表
        String chapterListUrl = getChapterListUrl(sourceBookId);
        String chapterListStr = getByHttpClientWithChrome(chapterListUrl);
        List<Map> bookIndexList = formatBookIndex(chapterListStr,book);
        List<BookIndex> bookIndexResult = new ArrayList<>();
        List<BookContent> bookContentResult = new ArrayList<>();
        bookIndexList.forEach(bookIndexSource -> {
            //获取章节内容
            String sourceChapterId = (String)bookIndexSource.get("chapterId");
            String chapterContentUrl = getChapterContent(sourceBookId, sourceChapterId);
            String chapterContentStr = getByHttpClientWithChrome(chapterContentUrl);
            //构建bookIndex
            BookIndex bookIndex = buildBookIndex(bookIndexSource, book);
            BookContent bookContent = buildBookContent(bookIndex, book, chapterContentStr);
            bookIndexResult.add(bookIndex);
            bookContentResult.add(bookContent);
        });
        if(CollectionUtil.isNotEmpty(bookIndexResult)){
            //获取最新的章节
            BookIndex lastBookIndex = bookIndexResult.get(bookIndexResult.size() - 1);
            book.setLastIndexId(lastBookIndex.getId());
            book.setLastIndexName(lastBookIndex.getIndexName());
            book.setLastIndexUpdateTime(lastBookIndex.getUpdateTime());
            //计算总字数
            int asInt = bookIndexResult.stream().mapToInt(BookIndex::getWordCount).reduce((x, y) -> x += y).getAsInt();
            book.setWordCount(asInt);
        }

        bookService.saveBookAndIndexAndContent(book, bookIndexResult, bookContentResult);
        return true;
    }

    public abstract String getBookListUrl();
    public abstract String getBookDetailUrl(String bookId);
    public abstract String getChapterListUrl(String bookId);

    public abstract String getChapterContent(String bookId,String chapterId);

    public abstract List<String> formatBookIdList(String bookIdListStr);


    public abstract List<Map> formatBookIndex(String bookIndexStr,Book book);

    public abstract Book formatBook(String bookId,String bookDetailStr);

    public abstract BookIndex buildBookIndex(Map<String,Object> bookIndexMap,Book book);

    public abstract BookContent buildBookContent(BookIndex bookIndex,Book book,String content);

    public Integer addWordCounts(BookIndex a ,BookIndex b){
        return a.getWordCount()+b.getWordCount();
    }


    @SneakyThrows
    public BookCategory getCrawlerBookCategory(String sourceCatId){
        Integer crawlerCatId = getCrawlerCatId(sourceCatId);
        return bookService.queryCategoryByCatId(crawlerCatId);
    }

    @SneakyThrows
    public Integer getCrawlerCatId(String sourceCatId){
        Integer sourceId = sourceId();
        CrawlSource crawlSource = crawlService.queryCrawlSource(sourceId);
        if(crawlSource == null){
            return CrawlerConstants.UNDEFINED_CATE_ID;
        }
        String crawlRule = crawlSource.getCrawlRule();
        if(StringUtils.isBlank(crawlRule)){
            return CrawlerConstants.UNDEFINED_CATE_ID;
        }
        RuleBean ruleBean = new ObjectMapper().readValue(crawlRule, RuleBean.class);
        Map<String, Map<String, Object>> jsonData = ruleBean.getJsonData();
        if(CollectionUtil.isEmpty(jsonData)){
            return CrawlerConstants.UNDEFINED_CATE_ID;
        }
        Map<String, Object> catIdRule = jsonData.get(CrawlerConstants.BOOK_CATEGORY);
        if(CollectionUtil.isEmpty(catIdRule) || catIdRule.get(sourceCatId) == null){
            return CrawlerConstants.UNDEFINED_CATE_ID;
        }
        return MapUtils.getInteger(catIdRule,sourceCatId);
    }

    @SneakyThrows
    public Byte getCrawlerBookStatus(String sourceBookStatus){
        Integer sourceId = sourceId();
        CrawlSource crawlSource = crawlService.queryCrawlSource(sourceId);
        if(crawlSource == null){
            return CrawlerConstants.DEFAULT_BOOK_STATUS;
        }
        String crawlRule = crawlSource.getCrawlRule();
        if(StringUtils.isBlank(crawlRule)){
            return CrawlerConstants.DEFAULT_BOOK_STATUS;
        }
        RuleBean ruleBean = new ObjectMapper().readValue(crawlRule, RuleBean.class);
        Map<String, Map<String, Object>> jsonData = ruleBean.getJsonData();
        if(CollectionUtil.isEmpty(jsonData)){
            return CrawlerConstants.DEFAULT_BOOK_STATUS;
        }
        Map<String, Object> bookStatusRule = jsonData.get(CrawlerConstants.BOOK_STATUS);
        if(CollectionUtil.isEmpty(bookStatusRule) || bookStatusRule.get(sourceBookStatus) == null){
            return CrawlerConstants.DEFAULT_BOOK_STATUS;
        }
        return MapUtils.getByte(bookStatusRule,sourceBookStatus);
    }


}
