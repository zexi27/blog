package com.zlq.blog.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

import lombok.*;

/**
 * 页面
 *
 * @author yezhiqiu
 * @date 2021/08/07
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@RequiredArgsConstructor()
@Builder
@TableName(value ="tb_page")
public class Page  {

    /**
     * 页面id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 页面名
     */
    @NonNull
    private String pageName;

    /**
     * 页面标签
     */
    @NonNull
    private String pageLabel;

    /**
     * 页面封面
     */
    private String pageCover;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @NonNull
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.UPDATE)
    @NonNull
    private LocalDateTime updateTime;

}