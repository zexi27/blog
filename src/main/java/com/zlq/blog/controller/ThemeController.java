package com.zlq.blog.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.zlq.blog.vo.PageVO;
import com.zlq.blog.vo.Result;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.zlq.blog.entity.ThemeEntity;
import com.zlq.blog.service.ThemeService;



/**
 *
 *
 * @author zhangliqun
 * @email yuzexi0727@gmail.com
 * @date 2022-12-22 13:43:46
 */
@RestController
@RequestMapping("/theme")
public class ThemeController {
    @Autowired
    private ThemeService themeService;

    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(@RequestParam Map<String, Object> params){
        return Result.ok(themeService.list());
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id){
		ThemeEntity theme = themeService.getById(id);

        return Result.ok(theme);
    }

    /**
     * 保存
     */
    @RequestMapping("/saveOrUpdate")
    public Result saveOrUpdateTheme(@RequestBody ThemeEntity theme){
		themeService.saveOrUpdateTheme(theme);

        return Result.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ThemeEntity theme){
		themeService.updateById(theme);

        return Result.ok();
    }

    @RequestMapping("/select/{id}")
    public Result selectTheme(@PathVariable Integer id){
        themeService.selectTheme(id);
        return Result.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(@RequestBody Integer[] ids){
        themeService.deleteTheme(ids);
		themeService.removeByIds(Arrays.asList(ids));

        return Result.ok();
    }

    @RequestMapping("/selected")
    public Result selected(){
        ThemeEntity theme = themeService.selected();
        return Result.ok(theme);
    }

}
