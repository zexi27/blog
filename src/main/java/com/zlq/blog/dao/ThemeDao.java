package com.zlq.blog.dao;

import com.zlq.blog.entity.ThemeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 *
 *
 * @author zhangliqun
 * @email yuzexi0727@gmail.com
 * @date 2022-12-22 13:43:46
 */
@Mapper
public interface ThemeDao extends BaseMapper<ThemeEntity> {

    Integer selectIsUsed(Integer[] ids);

}
