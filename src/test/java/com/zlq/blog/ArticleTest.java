package com.zlq.blog;

import com.zlq.blog.dao.ArticleDao;
import com.zlq.blog.entity.Article;
import com.zlq.blog.entity.ArticleOld;
import com.zlq.blog.service.ArticleService;
import com.zlq.blog.service.impl.ArticleServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @ProjectName:blog-springboot
 * @Package:com.zlq.blog
 * @ClassName: ArticleSimilarityTest
 * @description:
 * @author: LiQun
 * @CreateDate:2022/12/25 23:03
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ArticleTest {
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ArticleDao articleDao;
    @Autowired
    private ArticleService articleService;

    @Test
    void testSimilarity() {
        ResponseEntity<Object> response = restTemplate.exchange(
                "http://localhost:8081/list",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Object>() {
                });
        Object object = response.getBody();
        Map<String, Object> map = (LinkedHashMap) object;
        List<ArticleOld> articleOldList = (List<ArticleOld>) map.get("data");

        for (ArticleOld articleOld : articleOldList) {
            if (articleOld.getId() == 88) {
                // 进行比较
                Article article = articleDao.selectById(68);
//                LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
//                float distance = levenshteinDistance.getDistance(article.getArticleContent(), articleOld.getArticleContent());
//                System.out.println("文章相似度为：" + distance);
                System.out.println(article);
                System.out.println(articleOld);
            }
        }
    }

    @Test
    void testWordFrequency(){
//        Article article = articleDao.selectById(73);
//        String  word = "垃圾收集器";
//        Integer count = ArticleServiceImpl.statisticsWordCountOfArticle(word, article.getArticleContent());
//        System.out.println(word + "出现次数为：" + count);
    }
}
