package com.zlq.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * @ProjectName:blog-springboot
 * @Package:com.zlq.blog.config
 * @ClassName: PageRequireProperties
 * @description:
 * @author: LiQun
 * @CreateDate:2022/12/22 15:50
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "page")
public class PageRequireProperties {

    private Map<String,String> require;
}
