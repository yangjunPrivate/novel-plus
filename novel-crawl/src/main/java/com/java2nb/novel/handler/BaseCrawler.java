package com.java2nb.novel.handler;

import cn.hutool.core.collection.CollectionUtil;
import com.java2nb.novel.core.utils.HttpUtil;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookCategory;
import com.java2nb.novel.entity.BookContent;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.service.BookService;
import io.github.xxyopen.util.IdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.*;

import static com.java2nb.novel.core.utils.HttpUtil.getByHttpClientWithChrome;

/**
 * @Author Jun Yang
 * @Date 2022/1/25 3:58 下午
 * @Version 1.0
 */
public abstract class BaseCrawler implements Crawler{

    @Autowired
    BookService bookService;

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

    public BookCategory getCategoryId(Integer sourceCategoryId, Integer sourceId){
        return new BookCategory();
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
            String sourceChapterId = (String)bookIndexSource.get("sourceChapterId");
            String chapterContent = getChapterContent(sourceBookId, sourceChapterId);
            //构建bookIndex
            BookIndex bookIndex = buildBookIndex(bookIndexSource, book);
            BookContent bookContent = buildBookContent(bookIndex, book, chapterContent);
            bookIndexResult.add(bookIndex);
            bookContentResult.add(bookContent);
        });
        if(CollectionUtil.isNotEmpty(bookIndexResult)){
            //获取最新的章节
            BookIndex lastBookIndex = bookIndexResult.get(bookIndexResult.size() - 1);
            book.setLastIndexId(lastBookIndex.getId());
            book.setLastIndexName(lastBookIndex.getIndexName());
            book.setLastIndexUpdateTime(lastBookIndex.getUpdateTime());
        }
        //todo totalCounts
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


}
