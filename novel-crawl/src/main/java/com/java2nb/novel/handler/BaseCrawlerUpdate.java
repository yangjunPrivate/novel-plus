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
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.java2nb.novel.core.utils.HttpUtil.getByHttpClientWithChrome;

/**
 * @Author Jun Yang
 * @Date 2022/1/26 10:29 上午
 * @Version 1.0
 */
@Component
@Slf4j
public abstract class BaseCrawlerUpdate implements CrawlerUpdate{

    @Autowired
    BookService bookService;

    @Autowired
    CrawlService crawlService;

    public final IdWorker idWorker = IdWorker.INSTANCE;

    @Override
    public void doUpdate(String bookId) {
        //获取书籍信息
        String bookDetailUrl = getBookDetailUrl(bookId);
        String bookDetailStr = HttpUtil.getByHttpClientWithChrome(bookDetailUrl);
        Book newBook = formatBook(bookId, bookDetailStr);
        //对比 判断是否需要更新
        Book oldBook = bookService.findBySourceBookId(bookId);
        //将新数据封装到已有数据上
        buildBook(newBook,oldBook);

        //获取章节列表
        String chapterListUrl = getChapterListUrl(bookId);
        String chapterListStr = getByHttpClientWithChrome(chapterListUrl);
        List<Map> bookIndexList = formatBookIndex(chapterListStr,oldBook);

        List<BookIndex> updateBookIndex = new ArrayList<>();
        List<BookContent> updateBookContent = new ArrayList<>();
        bookIndexList.forEach(bookIndexSource -> {
            //获取章节内容
            String sourceChapterId = (String)bookIndexSource.get("sourceChapterId");
            //校验章节是否已经存在 存在跳过不处理
            boolean existBookIndex = bookService.queryIsExistBookIndex(oldBook.getId(), Integer.valueOf(sourceChapterId));
            if(existBookIndex){
                return;
            }
            String chapterContent = getChapterContent(bookId, sourceChapterId);
            //构建bookIndex
            BookIndex bookIndex = buildBookIndex(bookIndexSource, oldBook);
            BookContent bookContent = buildBookContent(bookIndex, oldBook, chapterContent);
            updateBookIndex.add(bookIndex);
            updateBookContent.add(bookContent);
        });
        if(CollectionUtil.isNotEmpty(updateBookIndex)){
            //获取最新的章节
            BookIndex lastBookIndex = updateBookIndex.get(updateBookIndex.size() - 1);
            oldBook.setLastIndexId(lastBookIndex.getId());
            oldBook.setLastIndexName(lastBookIndex.getIndexName());
            oldBook.setLastIndexUpdateTime(lastBookIndex.getUpdateTime());
            oldBook.setCrawlLastTime(new Date());
            //计算总字数
            int asInt = updateBookIndex.stream().mapToInt(BookIndex::getWordCount).reduce((x, y) -> x += y).getAsInt();
            oldBook.setWordCount(asInt);
        }

        if(CollectionUtil.isNotEmpty(updateBookIndex)){
            bookService.saveBookIndexAndContent(oldBook,updateBookIndex,updateBookContent);
        }

    }

    public abstract String getBookDetailUrl(String bookId);
    public abstract String getChapterListUrl(String bookId);

    public abstract String getChapterContent(String bookId,String chapterId);
    public abstract BookIndex buildBookIndex(Map<String,Object> bookIndexMap,Book book);

    public abstract BookContent buildBookContent(BookIndex bookIndex,Book book,String content);


    public abstract List<Map> formatBookIndex(String bookIndexStr, Book book);

    public abstract Book formatBook(String bookId,String bookDetailStr);

    public void buildBook(Book newBook,Book oldBook){
        if(StringUtils.isNoneBlank(newBook.getAuthorName())){
            oldBook.setAuthorName(newBook.getAuthorName());
        }
        if(StringUtils.isNoneBlank(newBook.getCatName())){
            oldBook.setCatName(newBook.getCatName());
        }
        if(newBook.getCatId() != null){
            oldBook.setCatId(newBook.getCatId());
        }
        if(StringUtils.isNoneBlank(newBook.getBookName())){
            oldBook.setBookName(newBook.getBookName());
        }
        if(StringUtils.isNoneBlank(newBook.getBookDesc())){
            oldBook.setBookDesc(oldBook.getBookDesc());
        }
        if(newBook.getBookStatus() != null){
            oldBook.setBookStatus(newBook.getBookStatus());
        }
        if(newBook.getIsVip() != null){
            oldBook.setIsVip(newBook.getIsVip());
        }
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
        Map<String, String> catIdRule = ruleBean.getCatIdRule();
        if(CollectionUtil.isEmpty(catIdRule) || StringUtils.isBlank(catIdRule.get(sourceCatId))){
            return CrawlerConstants.UNDEFINED_CATE_ID;
        }
        return Integer.valueOf(catIdRule.get(sourceCatId));
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
        Map<String, Byte> bookStatusRule = ruleBean.getBookStatusRule();
        if(CollectionUtil.isEmpty(bookStatusRule) || bookStatusRule.get(sourceBookStatus) == null){
            return CrawlerConstants.DEFAULT_BOOK_STATUS;
        }
        return bookStatusRule.get(sourceBookStatus);
    }

}
