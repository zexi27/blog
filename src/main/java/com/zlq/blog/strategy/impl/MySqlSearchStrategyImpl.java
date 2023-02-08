package com.zlq.blog.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.zlq.blog.dao.ArticleDao;
import com.zlq.blog.dto.ArticleSearchDTO;
import com.zlq.blog.dto.UserDetailDTO;
import com.zlq.blog.entity.Article;
import com.zlq.blog.strategy.SearchStrategy;
import com.zlq.blog.util.UserUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.zlq.blog.constant.CommonConst.*;
import static com.zlq.blog.enums.ArticleStatusEnum.PUBLIC;
import static com.zlq.blog.enums.ArticleStatusEnum.SECRET;

/**
 * mysql搜索策略
 *
 * @author yezhiqiu
 * @date 2021/07/27
 */
@Service("mySqlSearchStrategyImpl")
public class MySqlSearchStrategyImpl implements SearchStrategy {
    @Autowired
    private ArticleDao articleDao;

    @Override
    public List<ArticleSearchDTO> searchArticle(String keywords) {
        // 判空
        if (StringUtils.isBlank(keywords)) {
            return new ArrayList<>();
        }
        // 搜索文章
        List<Article> articleList = articleDao.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getIsDelete, FALSE)
                .eq(Article::getStatus, PUBLIC.getStatus())
                .and(i -> i.like(Article::getArticleTitle, keywords)
                        .or()
                        .like(Article::getArticleContent, keywords)));
        // 对于登录用户进行私密文章搜索
        if (UserUtils.isLogin()){
            UserDetailDTO loginUser = UserUtils.getLoginUser();
            List<Article> articleSecretList = articleDao.selectList(new LambdaQueryWrapper<Article>()
                    .eq(Article::getUserId, loginUser.getUserInfoId())
                    .eq(Article::getIsDelete, FALSE)
                    .eq(Article::getStatus, SECRET.getStatus())
                    .and(i -> i.like(Article::getArticleTitle, keywords)
                            .or()
                            .like(Article::getArticleContent, keywords)));
            if(!articleSecretList.isEmpty()){
                articleList.addAll(articleSecretList);
            }
        }

        // 高亮处理
        return articleList.stream().map(item -> {
            // 获取关键词第一次出现的位置
            String articleContent = item.getArticleContent();
            int index = item.getArticleContent().indexOf(keywords);
            if (index != -1) {
                // 获取关键词前面的文字
                int preIndex = index > 25 ? index - 25 : 0;
                String preText = item.getArticleContent().substring(preIndex, index);
                // 获取关键词到后面的文字
                int last = index + keywords.length();
                int postLength = item.getArticleContent().length() - last;
                int postIndex = postLength > 175 ? last + 175 : last + postLength;
                String postText = item.getArticleContent().substring(index, postIndex);
                // 文章内容高亮
                articleContent = (preText + postText).replaceAll(keywords, PRE_TAG + keywords + POST_TAG);
            }
            // 文章标题高亮
            String articleTitle = item.getArticleTitle().replaceAll(keywords, PRE_TAG + keywords + POST_TAG);
            return ArticleSearchDTO.builder()
                    .id(item.getId())
                    .articleTitle(articleTitle)
                    .articleContent(articleContent)
                    .build();
        }).collect(Collectors.toList());
    }

}
