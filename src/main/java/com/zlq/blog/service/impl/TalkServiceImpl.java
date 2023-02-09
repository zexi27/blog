package com.zlq.blog.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zlq.blog.dao.CommentDao;
import com.zlq.blog.dto.CommentCountDTO;
import com.zlq.blog.dto.TalkBackDTO;
import com.zlq.blog.dto.TalkDTO;
import com.zlq.blog.dto.UserDetailDTO;
import com.zlq.blog.entity.Talk;
import com.zlq.blog.enums.RoleEnum;
import com.zlq.blog.exception.BizException;
import com.zlq.blog.service.RedisService;
import com.zlq.blog.service.TalkService;
import com.zlq.blog.dao.TalkDao;
import com.zlq.blog.util.*;
import com.zlq.blog.vo.ConditionVO;
import com.zlq.blog.vo.PageResult;
import com.zlq.blog.vo.TalkVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.zlq.blog.constant.RedisPrefixConst.*;
import static com.zlq.blog.enums.TalkStatusEnum.PUBLIC;
import static com.zlq.blog.enums.TalkStatusEnum.SECRET;

/**
 * 说说服务
 *
 * @author yezhiqiu
 * @date 2022/01/23
 */
@Service
public class TalkServiceImpl extends ServiceImpl<TalkDao, Talk> implements TalkService {
    @Autowired
    private TalkDao talkDao;
    @Autowired
    private CommentDao commentDao;
    @Autowired
    private RedisService redisService;

    @Override
    public List<String> listHomeTalks() {
        // 查询最新公开的10条说说
        List<Talk> talkList = talkDao.selectList(new LambdaQueryWrapper<Talk>()
                .eq(Talk::getStatus, PUBLIC.getStatus())
                .orderByDesc(Talk::getIsTop)
                .orderByDesc(Talk::getId)
                .last("limit 10"));
        if (UserUtils.isLogin()) {
            Integer userId = UserUtils.getLoginUser().getUserInfoId();
            // 查询账号私密的10条说说
            List<Talk> secretList = talkDao.selectList(new LambdaQueryWrapper<Talk>()
                    .eq(Talk::getStatus, SECRET.getStatus())
                    .eq(Talk::getUserId, userId)
                    .orderByDesc(Talk::getIsTop)
                    .orderByDesc(Talk::getId)
                    .last("limit 10"));
            talkList.addAll(secretList);
        }
        List<String> list = talkList.stream()
                .sorted((o1, o2) -> o2.getIsTop() - o1.getIsTop())
                .sorted((o1, o2) -> o2.getId() - o1.getId())
                .map(item -> item.getContent().length() > 200 ? HTMLUtils.deleteHMTLTag(item.getContent().substring(0, 200)) : HTMLUtils.deleteHMTLTag(item.getContent()))
                .limit(10)
                .collect(Collectors.toList());
        return list;
    }

    @Override
    public PageResult<TalkDTO> listTalks() {
        // 查询说说总量
        Integer count = talkDao.selectCount((new LambdaQueryWrapper<Talk>()
                .eq(Talk::getStatus, PUBLIC.getStatus())));
        if (count == 0) {
            return new PageResult<>();
        }
        Integer userId = null;
        if (UserUtils.isLogin()) {
            userId = UserUtils.getLoginUser().getUserInfoId();
            Integer secretCount = talkDao.selectCount(new LambdaQueryWrapper<Talk>()
                    .eq(Talk::getStatus, SECRET.getStatus())
                    .eq(Talk::getUserId, userId));
            count += secretCount;
        }
        // 分页查询说说
        List<TalkDTO> talkDTOList = talkDao.listTalks(PageUtils.getLimitCurrent(), PageUtils.getSize(), userId);
        // 查询说说评论量
        List<Integer> talkIdList = talkDTOList.stream()
                .map(TalkDTO::getId)
                .collect(Collectors.toList());
        Map<Integer, Integer> commentCountMap = commentDao.listCommentCountByTopicIds(talkIdList)
                .stream()
                .collect(Collectors.toMap(CommentCountDTO::getId, CommentCountDTO::getCommentCount));
        // 查询说说点赞量
        Map<String, Object> likeCountMap = redisService.hGetAll(TALK_LIKE_COUNT);
        talkDTOList.forEach(item -> {
            item.setLikeCount((Integer) likeCountMap.get(item.getId().toString()));
            item.setCommentCount(commentCountMap.get(item.getId()));
            // 转换图片格式
            if (Objects.nonNull(item.getImages())) {
                item.setImgList(CommonUtils.castList(JSON.parseObject(item.getImages(), List.class), String.class));
            }
        });
        return new PageResult<>(talkDTOList, count);
    }

    @Override
    public TalkDTO getTalkById(Integer talkId) {
        Integer userId = null;
        if (UserUtils.isLogin()) {
            userId = UserUtils.getLoginUser().getUserInfoId();
        }
        // 查询说说信息
        TalkDTO talkDTO = talkDao.getTalkById(talkId,userId);
        if (Objects.isNull(talkDTO)) {
            throw new BizException("说说不存在");
        }
        // 查询说说点赞量
        talkDTO.setLikeCount((Integer) redisService.hGet(TALK_LIKE_COUNT, talkId.toString()));
        // 转换图片格式
        if (Objects.nonNull(talkDTO.getImages())) {
            talkDTO.setImgList(CommonUtils.castList(JSON.parseObject(talkDTO.getImages(), List.class), String.class));
        }
        return talkDTO;
    }

    @Override
    public void saveTalkLike(Integer talkId) {
        // 判断是否点赞
        String talkLikeKey = TALK_USER_LIKE + UserUtils.getLoginUser().getUserInfoId();
        if (redisService.sIsMember(talkLikeKey, talkId)) {
            // 点过赞则删除说说id
            redisService.sRemove(talkLikeKey, talkId);
            // 说说点赞量-1
            redisService.hDecr(TALK_LIKE_COUNT, talkId.toString(), 1L);
        } else {
            // 未点赞则增加说说id
            redisService.sAdd(talkLikeKey, talkId);
            // 说说点赞量+1
            redisService.hIncr(TALK_LIKE_COUNT, talkId.toString(), 1L);
        }
    }

    @Override
    public void saveOrUpdateTalk(TalkVO talkVO) {
        Talk talk = BeanCopyUtils.copyObject(talkVO, Talk.class);
        if (talkVO.getId() == null) {
            talk.setUserId(UserUtils.getLoginUser().getUserInfoId());
        }
        this.saveOrUpdate(talk);
    }

    @Override
    public void deleteTalks(List<Integer> talkIdList) {
        talkDao.deleteBatchIds(talkIdList);
    }

    @Override
    public PageResult<TalkBackDTO> listBackTalks(ConditionVO conditionVO) {
        UserDetailDTO loginUser = UserUtils.getLoginUser();
        Integer count = null;
        List<TalkBackDTO> talkDTOList = null;
        if (loginUser.getRoleList().contains(RoleEnum.ADMIN.getLabel())){
            // 查询说说总量
             count = talkDao.selectCount(new LambdaQueryWrapper<Talk>()
                    .eq(Objects.nonNull(conditionVO.getStatus()), Talk::getStatus, conditionVO.getStatus()));
            if (count == 0) {
                return new PageResult<>();
            }
            // 分页查询说说
            talkDTOList = talkDao.listBackTalks(PageUtils.getLimitCurrent(), PageUtils.getSize(), conditionVO,true,loginUser.getUserInfoId());

        }else {
            // 查询用户说说总量
            count = talkDao.selectCount(new LambdaQueryWrapper<Talk>()
                    .eq(Objects.nonNull(conditionVO.getStatus()), Talk::getStatus, conditionVO.getStatus())
                    .eq(Talk::getUserId,loginUser.getUserInfoId()));
            if (count == 0) {
                return new PageResult<>();
            }
            talkDTOList = talkDao.listBackTalks(PageUtils.getLimitCurrent(), PageUtils.getSize(), conditionVO, false, loginUser.getUserInfoId());

        }
        talkDTOList.forEach(item -> {
            // 转换图片格式
            if (Objects.nonNull(item.getImages())) {
                item.setImgList(CommonUtils.castList(JSON.parseObject(item.getImages(), List.class), String.class));
            }
        });
        return new PageResult<>(talkDTOList, count);

    }

    @Override
    public TalkBackDTO getBackTalkById(Integer talkId) {
        TalkBackDTO talkBackDTO = null;
        UserDetailDTO loginUser = UserUtils.getLoginUser();
        if (loginUser.getRoleList().contains(RoleEnum.ADMIN.getLabel())){
             talkBackDTO = talkDao.getBackTalkById(talkId,true,loginUser.getUserInfoId());
        }else {
            talkBackDTO = talkDao.getBackTalkById(talkId,false,loginUser.getUserInfoId());
        }
        // 转换图片格式
        if (Objects.nonNull(talkBackDTO.getImages())) {
            talkBackDTO.setImgList(CommonUtils.castList(JSON.parseObject(talkBackDTO.getImages(), List.class), String.class));
        }
        return talkBackDTO;
    }

}




