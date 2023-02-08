package com.zlq.blog.dao;

import com.zlq.blog.dto.TalkBackDTO;
import com.zlq.blog.dto.TalkDTO;
import com.zlq.blog.entity.Talk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zlq.blog.vo.ConditionVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 说说
 *
 * @author yezhiqiu
 * @date 2022/01/23
 */
@Repository
public interface TalkDao extends BaseMapper<Talk> {

    /**
     * 获取说说列表
     *
     * @param current 页码
     * @param size    大小
     * @return {@link List<TalkDTO>}
     */
    List<TalkDTO> listTalks(@Param("current") Long current, @Param("size") Long size, @Param("userId") Integer userId);

    /**
     * 查看后台说说
     *
     * @param current 页码
     * @param size    大小
     * @return {@link List<TalkBackDTO>}
     */
    List<TalkBackDTO> listBackTalks(@Param("current") Long current, @Param("size") Long size,
                                    @Param("condition") ConditionVO condition,
                                    @Param("isAdmin") Boolean isAdmin, @Param("userId") Integer userId);


    /**
     * 根据id查看说说
     *
     * @param talkId 说说id
     * @return {@link TalkDTO} 说说信息
     */
    TalkDTO getTalkById(@Param("talkId") Integer talkId, @Param("userId") Integer userId);


    /**
     * 根据id查看后台说说
     *
     * @param talkId 说说id
     * @return {@link TalkBackDTO} 说说信息
     */
    TalkBackDTO getBackTalkById(@Param("talkId") Integer talkId, @Param("isAdmin") Boolean isAdmin, @Param("userId") Integer userId);

}




