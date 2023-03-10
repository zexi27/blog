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
 * ????????????
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
//        // ??????????????????
//        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
//                .select(Article::getId, Article::getArticleTitle, Article::getCreateTime)
//                .eq(Article::getIsDelete, FALSE)
//                .eq(Article::getStatus, PUBLIC.getStatus());
//
//
//        Page<Article> articlePage = articleDao.selectPage(page, wrapper);
//
//        // ??????????????????????????????
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
            // ??????????????????
            count = articleDao.countArticleBacks(condition);
            // ??????????????????
            articleBackDTOList = articleDao.listArticleBacks(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);
        } else {
            Integer userInfoId = loginUser.getUserInfoId();
            // ??????????????????????????????
            count = articleDao.countUserArticleBacks(condition, userInfoId);
            // ??????????????????????????????
            articleBackDTOList = articleDao.listUserArticleBacks(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition, userInfoId);

        }

        // ?????????????????????????????????
        Map<Object, Double> viewsCountMap = redisService.zAllScore(ARTICLE_VIEWS_COUNT);
        Map<String, Object> likeCountMap = redisService.hGetAll(ARTICLE_LIKE_COUNT);
        // ???????????????????????????
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
        // ????????????
        List<ArticlePreviewDTO> articlePreviewDTOList = articleDao.listArticlesByCondition(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition, userId);
        // ?????????????????????(??????????????????)
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
        // ????????????????????????
        if (UserUtils.isLogin()){
            userId = UserUtils.getLoginUser().getUserInfoId();;
        }
        // ??????id????????????
        ArticleDTO article = articleDao.getArticleById(articleId,userId);
        if (Objects.isNull(article)) {
            throw new BizException("???????????????");
        }
        // ??????????????????
        CompletableFuture<List<ArticleRecommendDTO>> recommendArticleList = CompletableFuture.supplyAsync(() -> getArticleRecommendList(articleId));
        // ??????????????????
        CompletableFuture<List<ArticleRecommendDTO>> newestArticleList = CompletableFuture.supplyAsync(() -> {
            List<Article> articleList = articleDao.selectList(new LambdaQueryWrapper<Article>()
                    .select(Article::getId, Article::getArticleTitle, Article::getArticleCover, Article::getCreateTime)
                    .eq(Article::getIsDelete, FALSE)
                    .eq(Article::getStatus, PUBLIC.getStatus())
                    .orderByDesc(Article::getId).last("limit 5"));
            return BeanCopyUtils.copyList(articleList, ArticleRecommendDTO.class);
        });

        // ?????????????????????
        updateArticleViewsCount(articleId);
        // ??????????????????????????????
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
        // ???????????????????????????
        Double score = redisService.zScore(ARTICLE_VIEWS_COUNT, articleId);
        if (Objects.nonNull(score)) {
            article.setViewsCount(score.intValue());
        }
        article.setLikeCount((Integer) redisService.hGet(ARTICLE_LIKE_COUNT, articleId.toString()));
        // ??????????????????
        try {
            article.setRecommendArticleList(recommendArticleList.get());
            article.setNewestArticleList(newestArticleList.get());
        } catch (Exception e) {
            log.error(StrUtil.format("????????????:{}", ExceptionUtil.stacktraceToString(e)));
        }
        return article;
    }


    @Override
    public void saveArticleLike(Integer articleId) {
        // ??????????????????
        String articleLikeKey = ARTICLE_USER_LIKE + UserUtils.getLoginUser().getUserInfoId();
        if (redisService.sIsMember(articleLikeKey, articleId)) {
            // ????????????????????????id
            redisService.sRemove(articleLikeKey, articleId);
            // ???????????????-1
            redisService.hDecr(ARTICLE_LIKE_COUNT, articleId.toString(), 1L);
        } else {
            // ????????????????????????id
            redisService.sAdd(articleLikeKey, articleId);
            // ???????????????+1
            redisService.hIncr(ARTICLE_LIKE_COUNT, articleId.toString(), 1L);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveOrUpdateArticle(ArticleVO articleVO) {
        // ????????????????????????
        CompletableFuture<WebsiteConfigVO> webConfig = CompletableFuture.supplyAsync(() -> blogInfoService.getWebsiteConfig());

        // ??????????????????
        Category category = saveArticleCategory(articleVO);
        // ?????????????????????
        Article article = BeanCopyUtils.copyObject(articleVO, Article.class);
        if (Objects.nonNull(category)) {
            article.setCategoryId(category.getId());
        }
        // ????????????????????????
        if (StrUtil.isBlank(article.getArticleCover())) {
            try {
                article.setArticleCover(webConfig.get().getArticleCover());
            } catch (Exception e) {
                throw new BizException("??????????????????????????????");
            }
        }
        // ?????????????????????,???userId????????????????????????id,????????????????????????????????????????????????id
        if (articleVO.getId() == null){
            article.setUserId(UserUtils.getLoginUser().getUserInfoId());
        }else {
            article.setUserId(articleDao.selectById(articleVO.getId()).getUserId());
        }
        this.saveOrUpdate(article);
        // ??????????????????
        saveArticleTag(articleVO, article.getId());
        // ?????????????????????es
        if (searchMode.equals(SearchModeEnum.ELASTICSEARCH.getMode())) {
            //map?????????????????????type-????????????????????? data-??????article?????????
            Map<String, Object> map = new HashMap<>();
            map.put("type", "update");
            map.put("data", article);
            rabbitTemplate.convertAndSend(MAXWELL_EXCHANGE, "*", new Message(JSON.toJSONBytes(map), new MessageProperties()));
        }
    }

    /**
     * ??????????????????
     *
     * @param articleVO ????????????
     * @return {@link Category} ????????????
     */
    private Category saveArticleCategory(ArticleVO articleVO) {
        // ????????????????????????
        Category category = categoryDao.selectOne(new LambdaQueryWrapper<Category>().eq(Category::getCategoryName, articleVO.getCategoryName()));
        if (Objects.isNull(category) && !articleVO.getStatus().equals(DRAFT.getStatus())) {
            category = Category.builder().categoryName(articleVO.getCategoryName()).build();
            categoryDao.insert(category);
        }
        return category;
    }

    @Override
    public void updateArticleTop(ArticleTopVO articleTopVO) {
        // ????????????????????????
        Article article = Article.builder().id(articleTopVO.getId()).isTop(articleTopVO.getIsTop()).build();
        articleDao.updateById(article);
    }

    @Override
    public void updateArticleDelete(DeleteVO deleteVO) {
        // ??????????????????????????????
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
        // ????????????????????????
        articleTagDao.delete(new LambdaQueryWrapper<ArticleTag>().in(ArticleTag::getArticleId, articleIdList));
        // ????????????
        articleDao.deleteBatchIds(articleIdList);
    }

    @Override
    public List<String> exportArticles(List<Integer> articleIdList) {
        // ??????????????????
        List<Article> articleList = articleDao.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getArticleTitle, Article::getArticleContent)
                .in(Article::getId, articleIdList));
        // ?????????????????????
        List<String> urlList = new ArrayList<>();
        for (Article article : articleList) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(article.getArticleContent().getBytes())) {
                String url = uploadStrategyContext.executeUploadStrategy(article.getArticleTitle() + FileExtEnum.MD.getExtName(), inputStream, FilePathEnum.MD.getPath());
                urlList.add(url);
            } catch (Exception e) {
                log.error(StrUtil.format("??????????????????,??????:{}", ExceptionUtil.stacktraceToString(e)));
                throw new BizException("??????????????????");
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
            // ?????????????????????????????????????????????0.5?????????????????????????????????????????????
            LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
            for (Article article : articleList) {
                String oldContent = articleOld.getArticleContent();
                String articleContent = article.getArticleContent();
                if (Math.abs(oldContent.length() - articleContent.length()) > Math.min(oldContent.length(), articleContent.length())) {
                    continue;
                }
                float distance = levenshteinDistance.getDistance(oldContent, articleContent);
                // ??????????????????
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
            System.out.println("?????????" + count + "??????????????????" + (articleOlds.size() - count) + "?????????");

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
        // ??????????????????articleTag??????
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
                    System.out.println("id???88???????????????id???" + article.getId() + ",???????????????" + article.getArticleTitle()
                            + "??????????????????" + distance);
                }

            }

        }
    }

    @Override
    public List<String> automaticallyGenerateTags(String articleTitle, String articleContent) {
        List<Tag> tagList = tagDao.selectList(null);
        // ??????????????????????????????????????????
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
            // ??????????????????
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


    // Map???value???????????????
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

    // Map???value???????????????
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
        // ??????????????????????????????????????????
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
            // ??????????????????
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
        } else { // ??????????????????????????????????????????????????????

            // ???????????????????????????
            Tag tag = tagDao.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getTagName, TagConstant.DEFAULT_TAG));
            // ????????????????????????????????????
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
        // ??????????????????
        Article article = articleDao.selectById(articleId);
        if (!isAdmin && !article.getUserId().equals(loginUser.getUserInfoId())) {
            throw new BizException("???????????????????????????");
        }
        // ??????????????????
        Category category = categoryDao.selectById(article.getCategoryId());
        String categoryName = null;
        if (Objects.nonNull(category)) {
            categoryName = category.getCategoryName();
        }
        // ??????????????????
        List<String> tagNameList = tagDao.listTagNameByArticleId(articleId);
        // ????????????
        ArticleVO articleVO = BeanCopyUtils.copyObject(article, ArticleVO.class);
        articleVO.setCategoryName(categoryName);
        articleVO.setTagNameList(tagNameList);
        return articleVO;
    }


    /**
     * ?????????????????????
     *
     * @param articleId ??????id
     */
    public void updateArticleViewsCount(Integer articleId) {
        // ?????????????????????????????????????????????
        Set<Integer> articleSet = CommonUtils.castSet(Optional.ofNullable(session.getAttribute(ARTICLE_SET)).orElseGet(HashSet::new), Integer.class);
        if (!articleSet.contains(articleId)) {
            articleSet.add(articleId);
            session.setAttribute(ARTICLE_SET, articleSet);
            // ?????????+1
            redisService.zIncr(ARTICLE_VIEWS_COUNT, articleId, 1D);
        }
    }

    /**
     * ??????????????????
     *
     * @param articleVO ????????????
     */
    private void saveArticleTag(ArticleVO articleVO, Integer articleId) {
        // ???????????????????????????????????????
        if (Objects.nonNull(articleVO.getId())) {
            articleTagDao.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleVO.getId()));
        }
        // ??????????????????
        List<String> tagNameList = articleVO.getTagNameList();
        if (CollectionUtils.isNotEmpty(tagNameList)) {
            // ????????????????????????
            List<Tag> existTagList = tagService.list(new LambdaQueryWrapper<Tag>().in(Tag::getTagName, tagNameList));
            List<String> existTagNameList = existTagList.stream().map(Tag::getTagName).collect(Collectors.toList());
            List<Integer> existTagIdList = existTagList.stream().map(Tag::getId).collect(Collectors.toList());
            // ??????????????????????????????
            tagNameList.removeAll(existTagNameList);
            if (CollectionUtils.isNotEmpty(tagNameList)) {
                List<Tag> tagList = tagNameList.stream().map(item -> Tag.builder().tagName(item).build()).collect(Collectors.toList());
                tagService.saveBatch(tagList);
                List<Integer> tagIdList = tagList.stream().map(Tag::getId).collect(Collectors.toList());
                existTagIdList.addAll(tagIdList);
            }
            // ????????????id????????????
            List<ArticleTag> articleTagList = existTagIdList.stream().map(item -> ArticleTag.builder()
                            .articleId(articleId)
                            .tagId(item)
                            .build())
                    .collect(Collectors.toList());
            articleTagService.saveBatch(articleTagList);
        } else {
            // ?????????????????????????????????????????????????????????
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
                // ????????????????????????????????????????????????????????????????????????????????????
                String substring = content.substring(l, r);
                if (substring.equalsIgnoreCase(word)) res++;
            }
            l++;
            r++;
        }
        return res;
    }


    //????????????????????????
    private List<ArticleRecommendDTO> getArticleRecommendList(Integer articleId) {
        // 1.?????????????????????tag_id
        List<Integer> tagIdList = articleTagDao.selectList(new LambdaQueryWrapper<ArticleTag>()
                        .select(ArticleTag::getTagId)
                        .eq(ArticleTag::getArticleId, articleId))
                .stream().map(articleTag -> articleTag.getTagId()).collect(Collectors.toList());
        // 2.????????????????????????tag_id??????????????????id?????????id??????????????????????????????id??????????????????????????????????????????
        List<Integer> articleIdList = articleTagDao.selectList(new LambdaQueryWrapper<ArticleTag>()
                        .in(ArticleTag::getTagId, tagIdList)
                        .ne(ArticleTag::getArticleId, articleId))
                .stream().map(articleTag -> articleTag.getArticleId()).collect(Collectors.toList());
        // 3.?????????????????????id??????,?????????????????????id
        // 3.1 ????????????TreeMap???key??????????????????id???value????????????????????????????????????
        Map<Integer, Integer> map = new TreeMap<>((o1, o2) -> o2 - o1);
        for (Integer id : articleIdList) {
            map.put(id, map.getOrDefault(id, 0) + 1);
        }

        //???????????????????????????????????????id
        List<Integer> newArticleIdList = new ArrayList<>();
        for (Integer id : map.keySet()) {
            newArticleIdList.add(id);
        }

        // 4.??????????????????id?????????6???id
        int index = Math.min(newArticleIdList.size(), 6);
        newArticleIdList = newArticleIdList.subList(0, index);


        // 5.??????id??????????????????????????????????????????????????????
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
