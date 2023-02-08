package com.zlq.blog.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zlq.blog.config.PageRequireProperties;
import com.zlq.blog.constant.RedisPrefixConst;
import com.zlq.blog.dao.PageDao;
import com.zlq.blog.entity.Page;
import com.zlq.blog.entity.UserAuth;
import com.zlq.blog.exception.BizException;
import com.zlq.blog.service.PageService;
import com.zlq.blog.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.parameters.P;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;


import com.zlq.blog.dao.ThemeDao;
import com.zlq.blog.entity.ThemeEntity;
import com.zlq.blog.service.ThemeService;
import org.springframework.transaction.annotation.Transactional;

import static com.zlq.blog.constant.RedisPrefixConst.PAGE_COVER;


@Service("themeService")
public class ThemeServiceImpl extends ServiceImpl<ThemeDao, ThemeEntity> implements ThemeService {


    @Autowired
    private PageRequireProperties pageRequireProperties;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ThemeDao themeDao;
    @Autowired
    private PageService pageService;
    @Autowired
    private PageDao pageDao;

    /**
     * 新增主题
     *
     * @param theme
     */
    @Override
    public void saveOrUpdateTheme(ThemeEntity theme) {
        if (theme.getId() == null) {
            if (theme.getThemeTitle() == null) {
                throw new BizException("请输入主题名称");
            }
            // 初始化主题内容
            List<Page> pageList = new ArrayList<>();
            Map<String, String> pageRequire = pageRequireProperties.getRequire();
            Iterator<String> iterator = pageRequire.keySet().iterator();
            while (iterator.hasNext()) {
                String pageLabel = iterator.next();
                String pageName = pageRequire.get(pageLabel);
                pageList.add(new Page(pageName, pageLabel, LocalDateTime.now(), LocalDateTime.now()));
            }
            theme.setThemeContent(JSON.toJSONString(pageList));
            themeDao.insert(theme);
        } else {
            themeDao.updateById(theme);
        }

    }


    @Override
    @Transactional
    public void selectTheme(Integer id) {

        themeDao.update(new ThemeEntity(), new LambdaUpdateWrapper<ThemeEntity>()
                .set(ThemeEntity::getStatus, true)
                .eq(ThemeEntity::getId, id));
        themeDao.update(new ThemeEntity(), new LambdaUpdateWrapper<ThemeEntity>()
                .set(ThemeEntity::getStatus, false)
                .ne(ThemeEntity::getId, id));
        ThemeEntity themeEntity = themeDao.selectById(id);

        pageDao.delete(null); // 删除page表中的所有数据
        String themeContent = themeEntity.getThemeContent();
        List<Page> pageList = JSON.parseArray(themeContent, Page.class);
        pageService.saveBatch(pageList);// 将解析到的themeEntity中的page数据插入
        redisService.del(PAGE_COVER); // 清除redis中的页面缓存
    }

    @Override
    public void deleteTheme(Integer[] ids) {
        // 查询删除的主题中是否有在使用的主题，如果有，无法删除
        Integer count = themeDao.selectIsUsed(ids);
        if (count == 1) {
            throw new BizException("存在正在使用的主题，无法删除");
        } else {
            List<Integer> idList = Arrays.stream(ids).collect(Collectors.toList());
            themeDao.deleteBatchIds(idList);
        }
    }

    @Override
    public ThemeEntity selected() {
        ThemeEntity theme = themeDao.selectOne(new LambdaQueryWrapper<ThemeEntity>()
                .eq(ThemeEntity::getStatus, 1));
        return theme;
    }

}