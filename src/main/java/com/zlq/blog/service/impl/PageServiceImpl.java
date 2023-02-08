package com.zlq.blog.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zlq.blog.dao.PageDao;
import com.zlq.blog.dao.ThemeDao;
import com.zlq.blog.entity.Page;
import com.zlq.blog.entity.ThemeEntity;
import com.zlq.blog.service.PageService;
import com.zlq.blog.service.RedisService;
import com.zlq.blog.util.BeanCopyUtils;
import com.zlq.blog.vo.PageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Objects;

import static com.zlq.blog.constant.RedisPrefixConst.PAGE_COVER;

/**
 * 页面服务
 *
 * @author yezhiqiu
 * @date 2021/08/07
 */
@Service
public class PageServiceImpl extends ServiceImpl<PageDao, Page> implements PageService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private PageDao pageDao;
    @Autowired
    private ThemeDao themeDao;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveOrUpdatePage(PageVO pageVO) {
        Page page = BeanCopyUtils.copyObject(pageVO, Page.class);
        this.saveOrUpdate(page);
        // 删除缓存
        redisService.del(PAGE_COVER);
        // 重新修改主题中的theme_content
        ThemeEntity themeEntity = themeDao.selectOne(new LambdaQueryWrapper<ThemeEntity>()
                .eq(ThemeEntity::getStatus, true));
        List<Page> pageList = pageDao.selectList(null);
        themeEntity.setThemeContent(JSON.toJSONString(pageList));
        themeDao.updateById(themeEntity);
    }
    /*
    [{"createTime":1671703338313,"pageLabel":"home","pageName":"首页","updateTime":1671703338313},{"createTime":1671703338313,"pageLabel":"archive","pageName":"归档","updateTime":1671703338313},{"createTime":1671703338313,"pageLabel":"category","pageName":"分类","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"tag","pageName":"标签","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"album","pageName":"相册","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"link","pageName":"友链","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"about","pageName":"关于","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"message","pageName":"留言","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"user","pageName":"个人中心","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"articleList","pageName":"文章列表","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"talk","pageName":"说说","updateTime":1671703338314}]
    [{"createTime":1671703338313,"pageLabel":"home","pageName":"首页","updateTime":1671703338313},{"createTime":1671703338313,"pageLabel":"archive","pageName":"归档","updateTime":1671703338313},{"createTime":1671703338313,"pageLabel":"category","pageName":"分类","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"tag","pageName":"标签","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"album","pageName":"相册","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"link","pageName":"友链","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"about","pageName":"关于","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"message","pageName":"留言","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"user","pageName":"个人中心","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"articleList","pageName":"文章列表","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"talk","pageName":"说说","updateTime":1671703338314}]
    [{"createTime":1671703338313,"pageLabel":"home","pageName":"首页","updateTime":1671703338313},{"createTime":1671703338313,"pageLabel":"archive","pageName":"归档","updateTime":1671703338313},{"createTime":1671703338313,"pageLabel":"category","pageName":"分类","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"tag","pageName":"标签","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"album","pageName":"相册","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"link","pageName":"友链","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"about","pageName":"关于","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"message","pageName":"留言","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"user","pageName":"个人中心","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"articleList","pageName":"文章列表","updateTime":1671703338314},{"createTime":1671703338314,"pageLabel":"talk","pageName":"说说","updateTime":1671703338314}]
     */

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deletePage(Integer pageId) {
        pageDao.deleteById(pageId);
        // 删除缓存
        redisService.del(PAGE_COVER);
        // 重新修改主题中的theme_content
        ThemeEntity themeEntity = themeDao.selectOne(new LambdaQueryWrapper<ThemeEntity>()
                .eq(ThemeEntity::getStatus, true));
        List<Page> pageList = pageDao.selectList(null);
        themeEntity.setThemeContent(JSON.toJSONString(pageList));
        themeDao.updateById(themeEntity);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<PageVO> listPages() {
        List<PageVO> pageVOList;
        // 查找缓存信息，不存在则从mysql读取，更新缓存
        Object pageList = redisService.get(PAGE_COVER);
        if (Objects.nonNull(pageList)) {
            pageVOList = JSON.parseObject(pageList.toString(), List.class);
        } else {
            pageVOList = BeanCopyUtils.copyList(pageDao.selectList(null), PageVO.class);
            redisService.set(PAGE_COVER, JSON.toJSONString(pageVOList));
        }
        return pageVOList;
    }

}




