package com.zlq.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Field;

import java.util.Date;

/**
 * @ProjectName:blog-springboot
 * @Package:com.zlq.blog.entity
 * @ClassName: ArticleOld
 * @description:
 * @author: LiQun
 * @CreateDate:2022/12/25 22:49
 */
@Data
public class ArticleOld {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Field(name = "id")
    private Integer id;

    @ApiModelProperty(value = "作者")
    private Integer userId;

    @ApiModelProperty(value = "文章分类")
    private Integer categoryId;

    @ApiModelProperty(value = "文章缩略图")
    private String articleCover;

    @ApiModelProperty(value = "标题")
    @Field(name = "article_title")
    private String articleTitle;

    @ApiModelProperty(value = "内容")
    @Field(name = "article_content")
    private String articleContent;

    @ApiModelProperty(value = "发表时间")
    private Date createTime;

    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    @ApiModelProperty(value = "是否置顶 0否 1是")
    private Integer isTop;

    @ApiModelProperty(value = "是否为草稿 0否 1是")
    private Integer isDraft;

    @ApiModelProperty(value = "是否删除  0否 1是")
    @Field(name = "is_delete")
    private Integer isDelete;

}
