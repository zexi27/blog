package com.zlq.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zlq.blog.entity.ThemeEntity;

import java.util.Map;

/**
 *
 *
 * @author zhangliqun
 * @email yuzexi0727@gmail.com
 * @date 2022-12-22 13:43:46
 */
public interface ThemeService extends IService<ThemeEntity> {

    void saveOrUpdateTheme(ThemeEntity theme);


    void selectTheme(Integer id);

    void deleteTheme(Integer[] ids);

    ThemeEntity selected();
}

