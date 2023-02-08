package com.zlq.blog.dao;

import com.zlq.blog.dto.*;
import com.zlq.blog.entity.Article;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlq.blog.vo.ConditionVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * 文章
 *
 * @author yezhiqiu
 * @date 2021/08/10
 */
@Repository
public interface ArticleDao extends BaseMapper<Article> {

    /**
     * 查询首页文章
     *
     * @param current 页码
     * @param size    大小
     * @return 文章列表
     */
    List<ArticleHomeDTO> listArticles(@Param("current") Long current, @Param("size") Long size);


    /**
     * 查询首页文章（登录）
     *
     * @param size
     * @param loginUserId
     * @return
     */
    List<ArticleHomeDTO> listArticlesLogin(@Param("current") Long current, @Param("size") Long size, @Param("loginUserId") Integer loginUserId);

    /**
     * 根据id查询文章
     *
     * @param articleId 文章id
     * @return 文章信息
     */
    ArticleDTO getArticleById(@Param("articleId") Integer articleId,@Param("userId") Integer userId);

    /**
     * 根据条件查询文章
     *
     * @param current   页码
     * @param size      大小
     * @param condition 条件
     * @return 文章列表
     */
    List<ArticlePreviewDTO> listArticlesByCondition(@Param("current") Long current, @Param("size") Long size, @Param("condition") ConditionVO condition,@Param("userId") Integer userId);

    /**
     * 查询后台文章
     *
     * @param current   页码
     * @param size      大小
     * @param condition 条件
     * @return 文章列表
     */
    List<ArticleBackDTO> listArticleBacks(@Param("current") Long current, @Param("size") Long size, @Param("condition") ConditionVO condition );


    List<ArticleBackDTO> listUserArticleBacks(@Param("current") Long current, @Param("size") Long size, @Param("condition") ConditionVO condition ,@Param("userId")Integer userId);

    /**
     * 查询后台文章总量
     *
     * @param condition 条件
     * @return 文章总量
     */
    Integer countArticleBacks(@Param("condition") ConditionVO condition);

    /**
     * 查询非管理员的后台文章
     *
     * @param condition
     * @param userId
     * @return
     */
    Integer countUserArticleBacks(@Param("condition") ConditionVO condition, @Param("userId") Integer userId);

    /**
     * 查看文章的推荐文章
     *
     * @param articleId 文章id
     * @return 文章列表
     */
    List<ArticleRecommendDTO> listRecommendArticles(@Param("articleId") Integer articleId);

    /**
     * 文章统计
     *
     * @return {@link List<ArticleStatisticsDTO>} 文章统计结果
     */
    List<ArticleStatisticsDTO> listArticleStatistics();


    List<Article> listArchivesNoLogin(@Param("start") long start, @Param("size") long size);

    int selectArchivesNoLoginCount();

    int selectArchivesLoginCount(@Param("loginUserId") int loginUserId);


    List<Article> listArchivesLogin(@Param("start") long start, @Param("size") long size, @Param("loginUserId") int loginUserId);


}
