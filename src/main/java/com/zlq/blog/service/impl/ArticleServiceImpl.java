package com.zlq.blog.service.impl;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zlq.blog.constant.TagConstant;
import com.zlq.blog.dao.ArticleDao;
import com.zlq.blog.dao.ArticleTagDao;
import com.zlq.blog.dao.CategoryDao;
import com.zlq.blog.dao.TagDao;
import com.zlq.blog.dto.*;
import com.zlq.blog.entity.*;
import com.zlq.blog.enums.FileExtEnum;
import com.zlq.blog.enums.FilePathEnum;
import com.zlq.blog.enums.RoleEnum;
import com.zlq.blog.enums.SearchModeEnum;
import com.zlq.blog.exception.BizException;
import com.zlq.blog.service.*;
import com.zlq.blog.strategy.context.SearchStrategyContext;
import com.zlq.blog.strategy.context.UploadStrategyContext;
import com.zlq.blog.util.BeanCopyUtils;
import com.zlq.blog.util.CommonUtils;
import com.zlq.blog.util.PageUtils;
import com.zlq.blog.util.UserUtils;
import com.zlq.blog.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.apache.lucene.search.spell.LevenshteinDistance;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.zlq.blog.constant.CommonConst.ARTICLE_SET;
import static com.zlq.blog.constant.CommonConst.FALSE;
import static com.zlq.blog.constant.MQPrefixConst.ARTICLE_EXCHANGE;
import static com.zlq.blog.constant.MQPrefixConst.MAXWELL_EXCHANGE;
import static com.zlq.blog.constant.RedisPrefixConst.*;
import static com.zlq.blog.enums.ArticleStatusEnum.*;


/**
 * 文章服务
 *
 * @author yezhiqiu
 * @date 2021/08/10
 */
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleDao, Article> implements ArticleService {
    @Autowired
    private ArticleDao articleDao;
    @Autowired
    private CategoryDao categoryDao;
    @Autowired
    private TagDao tagDao;
    @Autowired
    private TagService tagService;
    @Autowired
    private ArticleTagDao articleTagDao;
    @Autowired
    private SearchStrategyContext searchStrategyContext;
    @Autowired
    private HttpSession session;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ArticleTagService articleTagService;
    @Autowired
    private BlogInfoService blogInfoService;
    @Autowired
    private UploadStrategyContext uploadStrategyContext;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Value("${search.mode}")
    private String searchMode;

//    @Override
//    public PageResult<ArchiveDTO> listArchives() {
//        Page<Article> page = new Page<>(PageUtils.getCurrent(), PageUtils.getSize());
//
//        // 获取分页数据
//        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
//                .select(Article::getId, Article::getArticleTitle, Article::getCreateTime)
//                .eq(Article::getIsDelete, FALSE)
//                .eq(Article::getStatus, PUBLIC.getStatus());
//
//
//        Page<Article> articlePage = articleDao.selectPage(page, wrapper);
//
//        // 查询用户是否已经登录
//        if (UserUtils.isLogin()) {
//            UserDetailDTO loginUser = UserUtils.getLoginUser();
//            List<Article> secretArticleList = articleDao.selectList(new LambdaQueryWrapper<Article>().
//                    select(Article::getId, Article::getArticleTitle, Article::getCreateTime)
//                    .eq(Article::getIsDelete, FALSE)
//                    .eq(Article::getStatus, SECRET.getStatus())
//                    .eq(Article::getUserId, loginUser.getUserInfoId()));
//            List<Article> articleList = articlePage.getRecords();
//            articleList.addAll(secretArticleList);
//            articlePage.setRecords(articleList);
//        }
//        List<ArchiveDTO> archiveDTOList = BeanCopyUtils.copyList(articlePage.getRecords(), ArchiveDTO.class);
//        return new PageResult<>(archiveDTOList, (int) articlePage.getTotal());
//    }

    @Override
    public PageResult<ArchiveDTO> listArchives() {
        Long current = PageUtils.getCurrent();
        Long size = PageUtils.getSize();
        List<Article> articleList = new ArrayList<>();
        int count = 0;
        if (!UserUtils.isLogin()) {
            count = articleDao.selectArchivesNoLoginCount();
            articleList = articleDao.listArchivesNoLogin((current - 1) * size, size);
        } else {
            UserDetailDTO loginUser = UserUtils.getLoginUser();

            Integer loginUserId = loginUser.getUserInfoId();
            count = articleDao.selectArchivesLoginCount(loginUserId);
            articleList = articleDao.listArchivesLogin((current - 1) * size, size, loginUserId);
        }
        List<ArchiveDTO> archiveDTOList = BeanCopyUtils.copyList(articleList, ArchiveDTO.class);
        return new PageResult<>(archiveDTOList, count);

    }

    @Override
    public PageResult<ArticleBackDTO> listArticleBacks(ConditionVO condition) {
        UserDetailDTO loginUser = UserUtils.getLoginUser();
        Boolean isAdmin = false;
        for (String role : loginUser.getRoleList()) {
            if (role.equals(RoleEnum.ADMIN.getLabel())) {
                isAdmin = true;
                break;
            }
        }
        Integer count = 0;
        List<ArticleBackDTO> articleBackDTOList = null;
        if (isAdmin) {
            // 查询文章总量
            count = articleDao.countArticleBacks(condition);
            // 查询后台文章
            articleBackDTOList = articleDao.listArticleBacks(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);
        } else {
            Integer userInfoId = loginUser.getUserInfoId();
            // 查询该用户的文章总量
            count = articleDao.countUserArticleBacks(condition, userInfoId);
            // 查询该用户的后台文章
            articleBackDTOList = articleDao.listUserArticleBacks(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition, userInfoId);

        }

        // 查询文章点赞量和浏览量
        Map<Object, Double> viewsCountMap = redisService.zAllScore(ARTICLE_VIEWS_COUNT);
        Map<String, Object> likeCountMap = redisService.hGetAll(ARTICLE_LIKE_COUNT);
        // 封装点赞量和浏览量
        articleBackDTOList.forEach(item -> {
            Double viewsCount = viewsCountMap.get(item.getId());
            if (Objects.nonNull(viewsCount)) {
                item.setViewsCount(viewsCount.intValue());
            }
            item.setLikeCount((Integer) likeCountMap.get(item.getId().toString()));
        });
        return new PageResult<>(articleBackDTOList, count);
    }

    @Override
    public List<ArticleHomeDTO> listArticles() {
        if (!UserUtils.isLogin()) {
            return articleDao.listArticles(PageUtils.getLimitCurrent(), PageUtils.getSize());
        } else {
            UserDetailDTO loginUser = UserUtils.getLoginUser();
            Integer userInfoId = loginUser.getUserInfoId();
            return articleDao.listArticlesLogin(PageUtils.getLimitCurrent(), PageUtils.getSize(), userInfoId);
        }
    }


    @Override
    public ArticlePreviewListDTO listArticlesByCondition(ConditionVO condition) {
        Integer userId = null;
        if (UserUtils.isLogin()) {
            UserDetailDTO loginUser = UserUtils.getLoginUser();
            userId = loginUser.getUserInfoId();
        }
        // 查询文章
        List<ArticlePreviewDTO> articlePreviewDTOList = articleDao.listArticlesByCondition(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition, userId);
        // 搜索条件对应名(标签或分类名)
        String name;
        if (Objects.nonNull(condition.getCategoryId())) {
            name = categoryDao.selectOne(new LambdaQueryWrapper<Category>().select(Category::getCategoryName)
                    .eq(Category::getId, condition.getCategoryId())).getCategoryName();
        } else {
            name = tagService.getOne(new LambdaQueryWrapper<Tag>()
                    .select(Tag::getTagName).eq(Tag::getId, condition.getTagId())).getTagName();
        }
        return ArticlePreviewListDTO
                .builder()
                .articlePreviewDTOList(articlePreviewDTOList)
                .name(name)
                .build();
    }

    @Override
    public ArticleDTO getArticleById(Integer articleId) {
        Integer userId = null;
        // 先检测要查的文章
        if (UserUtils.isLogin()){
            userId = UserUtils.getLoginUser().getUserInfoId();;
        }
        // 查询id对应文章
        ArticleDTO article = articleDao.getArticleById(articleId,userId);
        if (Objects.isNull(article)) {
            throw new BizException("文章不存在");
        }
        // 查询推荐文章
        CompletableFuture<List<ArticleRecommendDTO>> recommendArticleList = CompletableFuture.supplyAsync(() -> getArticleRecommendList(articleId));
        // 查询最新文章
        CompletableFuture<List<ArticleRecommendDTO>> newestArticleList = CompletableFuture.supplyAsync(() -> {
            List<Article> articleList = articleDao.selectList(new LambdaQueryWrapper<Article>()
                    .select(Article::getId, Article::getArticleTitle, Article::getArticleCover, Article::getCreateTime)
                    .eq(Article::getIsDelete, FALSE)
                    .eq(Article::getStatus, PUBLIC.getStatus())
                    .orderByDesc(Article::getId).last("limit 5"));
            return BeanCopyUtils.copyList(articleList, ArticleRecommendDTO.class);
        });

        // 更新文章浏览量
        updateArticleViewsCount(articleId);
        // 查询上一篇下一篇文章
        Article lastArticle = articleDao.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getArticleTitle, Article::getArticleCover).eq(Article::getIsDelete, FALSE)
                .eq(Article::getStatus, PUBLIC.getStatus())
                .lt(Article::getId, articleId)
                .orderByDesc(Article::getId).last("limit 1"));
        Article nextArticle = articleDao.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getArticleTitle, Article::getArticleCover).eq(Article::getIsDelete, FALSE)
                .eq(Article::getStatus, PUBLIC.getStatus())
                .gt(Article::getId, articleId).orderByAsc(Article::getId)
                .last("limit 1"));
        article.setLastArticle(BeanCopyUtils.copyObject(lastArticle, ArticlePaginationDTO.class));
        article.setNextArticle(BeanCopyUtils.copyObject(nextArticle, ArticlePaginationDTO.class));
        // 封装点赞量和浏览量
        Double score = redisService.zScore(ARTICLE_VIEWS_COUNT, articleId);
        if (Objects.nonNull(score)) {
            article.setViewsCount(score.intValue());
        }
        article.setLikeCount((Integer) redisService.hGet(ARTICLE_LIKE_COUNT, articleId.toString()));
        // 封装文章信息
        try {
            article.setRecommendArticleList(recommendArticleList.get());
            article.setNewestArticleList(newestArticleList.get());
        } catch (Exception e) {
            log.error(StrUtil.format("堆栈信息:{}", ExceptionUtil.stacktraceToString(e)));
        }
        return article;
    }


    @Override
    public void saveArticleLike(Integer articleId) {
        // 判断是否点赞
        String articleLikeKey = ARTICLE_USER_LIKE + UserUtils.getLoginUser().getUserInfoId();
        if (redisService.sIsMember(articleLikeKey, articleId)) {
            // 点过赞则删除文章id
            redisService.sRemove(articleLikeKey, articleId);
            // 文章点赞量-1
            redisService.hDecr(ARTICLE_LIKE_COUNT, articleId.toString(), 1L);
        } else {
            // 未点赞则增加文章id
            redisService.sAdd(articleLikeKey, articleId);
            // 文章点赞量+1
            redisService.hIncr(ARTICLE_LIKE_COUNT, articleId.toString(), 1L);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveOrUpdateArticle(ArticleVO articleVO) {
        // 查询博客配置信息
        CompletableFuture<WebsiteConfigVO> webConfig = CompletableFuture.supplyAsync(() -> blogInfoService.getWebsiteConfig());

        // 保存文章分类
        Category category = saveArticleCategory(articleVO);
        // 保存或修改文章
        Article article = BeanCopyUtils.copyObject(articleVO, Article.class);
        if (Objects.nonNull(category)) {
            article.setCategoryId(category.getId());
        }
        // 设定默认文章封面
        if (StrUtil.isBlank(article.getArticleCover())) {
            try {
                article.setArticleCover(webConfig.get().getArticleCover());
            } catch (Exception e) {
                throw new BizException("设定默认文章封面失败");
            }
        }
        // 如果是新增文章,将userId设为当前登录用户id,如果是修改文章，还是用之前的用户id
        if (articleVO.getId() == null){
            article.setUserId(UserUtils.getLoginUser().getUserInfoId());
        }else {
            article.setUserId(articleDao.selectById(articleVO.getId()).getUserId());
        }
        this.saveOrUpdate(article);
        // 保存文章标签
        saveArticleTag(articleVO, article.getId());
        // 同步文章数据到es
        if (searchMode.equals(SearchModeEnum.ELASTICSEARCH.getMode())) {
            //map中存放两个键，type-存放操作类型， data-存放article信息，
            Map<String, Object> map = new HashMap<>();
            map.put("type", "update");
            map.put("data", article);
            rabbitTemplate.convertAndSend(MAXWELL_EXCHANGE, "*", new Message(JSON.toJSONBytes(map), new MessageProperties()));
        }
    }

    /**
     * 保存文章分类
     *
     * @param articleVO 文章信息
     * @return {@link Category} 文章分类
     */
    private Category saveArticleCategory(ArticleVO articleVO) {
        // 判断分类是否存在
        Category category = categoryDao.selectOne(new LambdaQueryWrapper<Category>().eq(Category::getCategoryName, articleVO.getCategoryName()));
        if (Objects.isNull(category) && !articleVO.getStatus().equals(DRAFT.getStatus())) {
            category = Category.builder().categoryName(articleVO.getCategoryName()).build();
            categoryDao.insert(category);
        }
        return category;
    }

    @Override
    public void updateArticleTop(ArticleTopVO articleTopVO) {
        // 修改文章置顶状态
        Article article = Article.builder().id(articleTopVO.getId()).isTop(articleTopVO.getIsTop()).build();
        articleDao.updateById(article);
    }

    @Override
    public void updateArticleDelete(DeleteVO deleteVO) {
        // 修改文章逻辑删除状态
        List<Article> articleList = deleteVO.getIdList().stream().map(id -> Article.builder()
                        .id(id)
                        .isTop(FALSE)
                        .isDelete(deleteVO.getIsDelete())
                        .build())
                .collect(Collectors.toList());
        if (searchMode.equals(SearchModeEnum.ELASTICSEARCH.getMode())) {
            for (Article article : articleList) {
                article = articleDao.selectById(article.getId());
                article.setIsDelete(deleteVO.getIsDelete() == 1 ? 1 : 0);
                Map<String, Object> map = new HashMap<>();
                map.put("type", deleteVO.getIsDelete() == 1 ? "delete" : "update");
                map.put("data", article);
                rabbitTemplate.convertAndSend(MAXWELL_EXCHANGE, "*", new Message(JSON.toJSONBytes(map), new MessageProperties()));
            }
        }
        this.updateBatchById(articleList);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteArticles(List<Integer> articleIdList) {
        // 删除文章标签关联
        articleTagDao.delete(new LambdaQueryWrapper<ArticleTag>().in(ArticleTag::getArticleId, articleIdList));
        // 删除文章
        articleDao.deleteBatchIds(articleIdList);
    }

    @Override
    public List<String> exportArticles(List<Integer> articleIdList) {
        // 查询文章信息
        List<Article> articleList = articleDao.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getArticleTitle, Article::getArticleContent)
                .in(Article::getId, articleIdList));
        // 写入文件并上传
        List<String> urlList = new ArrayList<>();
        for (Article article : articleList) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(article.getArticleContent().getBytes())) {
                String url = uploadStrategyContext.executeUploadStrategy(article.getArticleTitle() + FileExtEnum.MD.getExtName(), inputStream, FilePathEnum.MD.getPath());
                urlList.add(url);
            } catch (Exception e) {
                log.error(StrUtil.format("导入文章失败,堆栈:{}", ExceptionUtil.stacktraceToString(e)));
                throw new BizException("导出文章失败");
            }
        }
        return urlList;
    }

    @Override
    public Integer importArticlesFromOldDatabase() {
        ResponseEntity<Object> response = restTemplate.exchange(
                "http://localhost:8081/list",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Object>() {
                });
        Object object = response.getBody();
        Map<String, Object> map = (LinkedHashMap) object;
        Object data = map.get("data");
        String jsonString = JSON.toJSONString(data);
        List<ArticleOld> articleOlds = JSON.parseArray(jsonString, ArticleOld.class);
        List<Article> articleList = articleDao.selectList(null);
        int transferCount = articleOlds.size();
        int count = 0;
        List<String> articleTitleList = articleOlds.stream().map(articleOld -> {
            return articleOld.getArticleTitle();
        }).collect(Collectors.toList());
        for (ArticleOld articleOld : articleOlds) {
            boolean flag = true;
            // 对比文章相关度，如果相关度超过0.5肯定为之前写过的文章，无需添加
            LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
            for (Article article : articleList) {
                String oldContent = articleOld.getArticleContent();
                String articleContent = article.getArticleContent();
                if (Math.abs(oldContent.length() - articleContent.length()) > Math.min(oldContent.length(), articleContent.length())) {
                    continue;
                }
                float distance = levenshteinDistance.getDistance(oldContent, articleContent);
                // 存在重复文章
                if (distance > 0.5) {
                    flag = false;
                    transferCount--;
                    articleTitleList.remove(articleOld.getArticleTitle());
                    break;
                }
            }
            if (flag) {
                transferArticle(articleOld);
            }
            count++;
            System.out.println("解析完" + count + "篇文章，还剩" + (articleOlds.size() - count) + "篇文章");

        }
        System.out.println(articleTitleList);
        return transferCount;
    }

    private void transferArticle(ArticleOld articleOld) {
        Article article = Article.builder()
                .userId(articleOld.getUserId())
                .categoryId(articleOld.getCategoryId())
                .articleCover(articleOld.getArticleCover())
                .articleTitle(articleOld.getArticleTitle())
                .articleContent(articleOld.getArticleContent())
                .originalUrl(null)
                .isTop(FALSE)
                .isDelete(FALSE)
                .status(1)
                .createTime(articleOld.getCreateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
                .build();
        if (articleOld.getUpdateTime() != null) {
            Date updateTime = articleOld.getUpdateTime();
            article.setUpdateTime(updateTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        articleDao.insert(article);
        // 获取该文章的articleTag信息
        ResponseEntity<Object> response = restTemplate.exchange(
                "http://localhost:8081/getArticleTag/" + articleOld.getId(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Object>() {
                });
        Object object = response.getBody();
        Map<String, Object> map = (LinkedHashMap) object;
        Object data = map.get("data");
        String jsonString = JSON.toJSONString(data);
        List<ArticleTag> articleTagList = JSON.parseArray(jsonString, ArticleTag.class);
        articleTagList.forEach(articleTag -> {
            articleTag = ArticleTag.builder()
                    .articleId(article.getId())
                    .tagId(articleTag.getTagId())
                    .build();
            articleTagDao.insert(articleTag);
        });

    }

    @Override
    public void testSimilarity() {
        ResponseEntity<Object> response = restTemplate.exchange(
                "http://localhost:8081/list",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Object>() {
                });
        Object object = response.getBody();
        Map<String, Object> map = (LinkedHashMap) object;
        Object data = map.get("data");
        String jsonString = JSON.toJSONString(data);
        List<ArticleOld> articleOlds = JSON.parseArray(jsonString, ArticleOld.class);
        for (ArticleOld articleOld : articleOlds) {
            if (articleOld.getId() == 88) {
                List<Article> articles = articleDao.selectList(null);
                LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

                for (Article article : articles) {
                    float distance = levenshteinDistance.getDistance(articleOld.getArticleContent(), article.getArticleContent());
                    System.out.println("id为88的就文章与id为" + article.getId() + ",文章标题为" + article.getArticleTitle()
                            + "的相似度为：" + distance);
                }

            }

        }
    }

    @Override
    public List<String> automaticallyGenerateTags(String articleTitle, String articleContent) {
        List<Tag> tagList = tagDao.selectList(null);
        // 统计每个标签关键字出现的频率
        Map<Tag, Integer> map = new HashMap<>();
        tagList.forEach(tag -> {
            String tagName = tag.getTagName();
            Integer tagNameCount = statisticsWordCountOfArticle(tagName, articleTitle, articleContent);
            if (tagNameCount > 0) {
                map.put(tag, tagNameCount);
            }
        });
        List<String> tagNameList = new ArrayList<>();

        if (!map.isEmpty()) {
            Map<Tag, Integer> sortDescendMap = sortDescend(map);
            // 存放三个标签
            Iterator<Tag> iterator = sortDescendMap.keySet().iterator();
            for (int i = 0; i < 3; i++) {
                if (!iterator.hasNext()) {
                    break;
                }
                Tag tag = iterator.next();
                tagNameList.add(tag.getTagName());
            }
        } else {
            tagNameList.add(TagConstant.DEFAULT_TAG);
        }
        return tagNameList;
    }


    // Map的value值降序排序
    public static <K, V extends Comparable<? super V>> Map<K, V> sortDescend(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
            @Override
            public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
                int compare = (o1.getValue()).compareTo(o2.getValue());
                return -compare;
            }
        });

        Map<K, V> returnMap = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> entry : list) {
            returnMap.put(entry.getKey(), entry.getValue());
        }
        return returnMap;
    }

    // Map的value值升序排序
    public static <K, V extends Comparable<? super V>> Map<K, V> sortAscend(Map<K, V> map) {
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, (o1, o2) ->   (o1.getValue()).compareTo(o2.getValue()));

        Map<K, V> returnMap = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> entry : list) {
            returnMap.put(entry.getKey(), entry.getValue());
        }
        return returnMap;
    }

    private void automaticallySelectOrCreateTags(ArticleVO articleVO, Integer articleId) {
        String articleTitle = articleVO.getArticleTitle();
        String articleContent = articleVO.getArticleContent();
        List<Tag> tagList = tagDao.selectList(null);
        // 统计每个标签关键字出现的频率
        Map<Tag, Integer> map = new HashMap<>();
        tagList.forEach(tag -> {
            String tagName = tag.getTagName();
            Integer tagNameCount = statisticsWordCountOfArticle(tagName, articleTitle, articleContent);
            if (tagNameCount > 0) {
                map.put(tag, tagNameCount);
            }
        });
        if (!map.isEmpty()) {
            Map<Tag, Integer> sortDescendMap = sortDescend(map);
            // 存放三个标签
            Iterator<Tag> iterator = sortDescendMap.keySet().iterator();
            for (int i = 0; i < 3; i++) {
                if (!iterator.hasNext()) {
                    break;
                }
                Tag tag = iterator.next();
                ArticleTag articleTag = ArticleTag.builder()
                        .articleId(articleId)
                        .tagId(tag.getId())
                        .build();
                articleTagDao.insert(articleTag);
            }
        } else { // 如果没有合适的标签，随机选择一个标签

            // 查询是否有默认标签
            Tag tag = tagDao.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getTagName, TagConstant.DEFAULT_TAG));
            // 没有默认标签选择随机标签
            if (Objects.isNull(tag)) {
                tag = tagDao.selectOne(new LambdaQueryWrapper<Tag>().last("ORDER BY RAND() LIMIT 1"));
            }
            ArticleTag articleTag = ArticleTag.builder()
                    .articleId(articleId)
                    .tagId(tag.getId())
                    .build();
            articleTagDao.insert(articleTag);
        }
    }

    @Override
    public List<ArticleSearchDTO> listArticlesBySearch(ConditionVO condition) {
        return searchStrategyContext.executeSearchStrategy(condition.getKeywords());
    }

    @Override
    public ArticleVO getArticleBackById(Integer articleId) {
        UserDetailDTO loginUser = UserUtils.getLoginUser();
        Boolean isAdmin = false;
        for (String role : loginUser.getRoleList()) {
            if (role.equals(RoleEnum.ADMIN.getLabel())) {
                isAdmin = true;
                break;
            }
        }
        // 查询文章信息
        Article article = articleDao.selectById(articleId);
        if (!isAdmin && !article.getUserId().equals(loginUser.getUserInfoId())) {
            throw new BizException("这篇文章不属于你！");
        }
        // 查询文章分类
        Category category = categoryDao.selectById(article.getCategoryId());
        String categoryName = null;
        if (Objects.nonNull(category)) {
            categoryName = category.getCategoryName();
        }
        // 查询文章标签
        List<String> tagNameList = tagDao.listTagNameByArticleId(articleId);
        // 封装数据
        ArticleVO articleVO = BeanCopyUtils.copyObject(article, ArticleVO.class);
        articleVO.setCategoryName(categoryName);
        articleVO.setTagNameList(tagNameList);
        return articleVO;
    }


    /**
     * 更新文章浏览量
     *
     * @param articleId 文章id
     */
    public void updateArticleViewsCount(Integer articleId) {
        // 判断是否第一次访问，增加浏览量
        Set<Integer> articleSet = CommonUtils.castSet(Optional.ofNullable(session.getAttribute(ARTICLE_SET)).orElseGet(HashSet::new), Integer.class);
        if (!articleSet.contains(articleId)) {
            articleSet.add(articleId);
            session.setAttribute(ARTICLE_SET, articleSet);
            // 浏览量+1
            redisService.zIncr(ARTICLE_VIEWS_COUNT, articleId, 1D);
        }
    }

    /**
     * 保存文章标签
     *
     * @param articleVO 文章信息
     */
    private void saveArticleTag(ArticleVO articleVO, Integer articleId) {
        // 编辑文章则删除文章所有标签
        if (Objects.nonNull(articleVO.getId())) {
            articleTagDao.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleVO.getId()));
        }
        // 添加文章标签
        List<String> tagNameList = articleVO.getTagNameList();
        if (CollectionUtils.isNotEmpty(tagNameList)) {
            // 查询已存在的标签
            List<Tag> existTagList = tagService.list(new LambdaQueryWrapper<Tag>().in(Tag::getTagName, tagNameList));
            List<String> existTagNameList = existTagList.stream().map(Tag::getTagName).collect(Collectors.toList());
            List<Integer> existTagIdList = existTagList.stream().map(Tag::getId).collect(Collectors.toList());
            // 对比新增不存在的标签
            tagNameList.removeAll(existTagNameList);
            if (CollectionUtils.isNotEmpty(tagNameList)) {
                List<Tag> tagList = tagNameList.stream().map(item -> Tag.builder().tagName(item).build()).collect(Collectors.toList());
                tagService.saveBatch(tagList);
                List<Integer> tagIdList = tagList.stream().map(Tag::getId).collect(Collectors.toList());
                existTagIdList.addAll(tagIdList);
            }
            // 提取标签id绑定文章
            List<ArticleTag> articleTagList = existTagIdList.stream().map(item -> ArticleTag.builder()
                            .articleId(articleId)
                            .tagId(item)
                            .build())
                    .collect(Collectors.toList());
            articleTagService.saveBatch(articleTagList);
        } else {
            // 如果没有传入标签，则自动选择或创建标签
            automaticallySelectOrCreateTags(articleVO, articleId);
        }
    }


    private Integer statisticsWordCountOfArticle(String word, String articleTitle, String articleContent) {
        int length = word.length();
        int l = 0, r = length;
        int res = 0;
        StringBuilder contentBuilder = new StringBuilder(articleTitle);
        contentBuilder.append(articleContent);
        String content = contentBuilder.toString();
        while (r <= content.length()) {
            if (content.charAt(l) + 32 == word.charAt(0) || content.charAt(l) - 32 == word.charAt(0) || content.charAt(l) == word.charAt(0)) {
                // 如果范围中第一个字符和搜索词中的第一个字符相同则进行比较
                String substring = content.substring(l, r);
                if (substring.equalsIgnoreCase(word)) res++;
            }
            l++;
            r++;
        }
        return res;
    }


    //智能推荐相关文章
    private List<ArticleRecommendDTO> getArticleRecommendList(Integer articleId) {
        // 1.获取当前文章的tag_id
        List<Integer> tagIdList = articleTagDao.selectList(new LambdaQueryWrapper<ArticleTag>()
                        .select(ArticleTag::getTagId)
                        .eq(ArticleTag::getArticleId, articleId))
                .stream().map(articleTag -> articleTag.getTagId()).collect(Collectors.toList());
        // 2.获取在当前文章的tag_id范围内的文章id，文章id可能会有重复值，文章id重复次数越多，说明相关度越高
        List<Integer> articleIdList = articleTagDao.selectList(new LambdaQueryWrapper<ArticleTag>()
                        .in(ArticleTag::getTagId, tagIdList)
                        .ne(ArticleTag::getArticleId, articleId))
                .stream().map(articleTag -> articleTag.getArticleId()).collect(Collectors.toList());
        // 3.将集合中的文章id去重,获取推荐的文章id
        // 3.1 定义一个TreeMap，key用来存放文章id，value用来存储推荐文章的相关度
        Map<Integer, Integer> map = new TreeMap<>((o1, o2) -> o2 - o1);
        for (Integer id : articleIdList) {
            map.put(id, map.getOrDefault(id, 0) + 1);
        }

        //存放依据相关度排序后的文章id
        List<Integer> newArticleIdList = new ArrayList<>();
        for (Integer id : map.keySet()) {
            newArticleIdList.add(id);
        }

        // 4.获取推荐文章id中的前6个id
        int index = Math.min(newArticleIdList.size(), 6);
        newArticleIdList = newArticleIdList.subList(0, index);


        // 5.根据id查询每个推荐文章，放入推荐文章集合中
        List<Article> articleList = new ArrayList<>();
        for (Integer id : newArticleIdList) {
            Article article = articleDao.selectOne(new LambdaQueryWrapper<Article>()
                    .select(Article::getId,
                            Article::getArticleCover,
                            Article::getArticleTitle,
                            Article::getCreateTime)
                    .eq(Article::getId, id)
                    .eq(Article::getIsDelete, FALSE));
            if (null != article) {
                articleList.add(article);
            }
        }
        List<ArticleRecommendDTO> articleRecommendList = BeanCopyUtils.copyList(articleList, ArticleRecommendDTO.class);
        return articleRecommendList;
    }


}
