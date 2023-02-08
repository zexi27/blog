package com.zlq.blog.entity;

import com.alibaba.fastjson.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 *
 *
 * @author zhangliqun
 * @email yuzexi0727@gmail.com
 * @date 2022-12-22 13:43:46
 */
@Data
@TableName("tb_theme")
public class ThemeEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 主题id
	 */
	@JSONField
	@TableId(value = "id", type = IdType.AUTO)
	private Integer id;
	/**
	 * 主题标题
	 */
	@JSONField
	private String themeTitle;
	/**
	 * 主题内容
	 */
	@JSONField
	private String themeContent;
	/**
	 * 使用状态

	 */
	@JSONField
	private Integer status;
	/**
	 *
	 */
	@JSONField
	private Date createTime;
	/**
	 *
	 */
	@JSONField
	private Date updateTime;

}
