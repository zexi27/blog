package com.zlq.blog;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.gson.Gson;
import com.zlq.blog.config.PageRequireProperties;
import com.zlq.blog.dao.PageDao;
import com.zlq.blog.dao.ThemeDao;
import com.zlq.blog.entity.Page;
import com.zlq.blog.entity.ThemeEntity;
import com.zlq.blog.service.ThemeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @ProjectName:blog-springboot
 * @Package:com.zlq.blog
 * @ClassName: ThemeTest
 * @description:
 * @author: LiQun
 * @CreateDate:2022/12/22 17:06
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ThemeTest {
    @Autowired
    private ThemeDao themeDao;
    @Autowired
    private ThemeService themeService;
    @Autowired
    private PageDao pageDao;

    @Autowired
    private PageRequireProperties pageRequireProperties;

    @Test
    void testHandleTheme() {
        List<Page> pageList = new ArrayList<>();
        Map<String, String> pageRequire = pageRequireProperties.getRequire();
        Iterator<String> iterator = pageRequire.keySet().iterator();
        while (iterator.hasNext()) {
            String pageName = iterator.next();
            String pageLabel = pageRequire.get(pageName);
            pageList.add(new Page(pageName, pageLabel, LocalDateTime.now(), LocalDateTime.now()));
        }
        String pageString = JSON.toJSONString(pageList);
        System.out.println(pageString);
        List<Page> pages = JSON.parseArray(pageString, Page.class);
        System.out.println(pages);
    }

    @Test
    void test(){
        List<Page> pages = pageDao.selectList(null);
        List<ThemeEntity> themeEntities = themeDao.selectList(null);
        ThemeEntity themeEntity = themeEntities.get(0);
        themeEntity.setThemeContent(JSON.toJSONString(pages));
        themeDao.updateById(themeEntity);
    }
}
