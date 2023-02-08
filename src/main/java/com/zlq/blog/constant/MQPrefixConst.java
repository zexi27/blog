package com.zlq.blog.constant;

/**
 * mqprefix常量
 * mq常量
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
public class MQPrefixConst {

    /**
     * article交换机
     */
    public static final String ARTICLE_EXCHANGE = "blog.article.exchange";

    /**
     * maxwell交换机
     */
    public static final String MAXWELL_EXCHANGE = "maxwell_exchange";

    /**
     * maxwell队列
     */
    public static final String MAXWELL_QUEUE = "maxwell_queue";

    /**
     * email交换机
     */
    public static final String EMAIL_EXCHANGE = "email_exchange";

    /**
     * 邮件队列
     */
    public static final String EMAIL_QUEUE = "email_queue";

}
