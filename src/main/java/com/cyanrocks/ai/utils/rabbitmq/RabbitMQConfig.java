package com.cyanrocks.ai.utils.rabbitmq;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author wjq
 * @Date 2026/1/7 13:33
 */
@Configuration
public class RabbitMQConfig {

    public static final String PDF_PROCESS_QUEUE = "pdf.process.queue";
    public static final String PDF_DLQ = "pdf.process.dlq";
    public static final String PDF_EXCHANGE = "pdf.exchange";

    // 主队列（持久化 + 死信路由）
    @Bean
    public Queue pdfProcessQueue() {
        return QueueBuilder.durable(PDF_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", PDF_DLQ)
                .build();
    }

    // 死信队列（用于存储处理失败的消息）
    @Bean
    public Queue pdfDlq() {
        return QueueBuilder.durable(PDF_DLQ).build();
    }

    // 交换机（简单场景可不用，直接 queue 发送）
    @Bean
    public DirectExchange pdfExchange() {
        return new DirectExchange(PDF_EXCHANGE);
    }

    @Bean("pdfContainerFactory")
    public SimpleRabbitListenerContainerFactory pdfContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(5);
        factory.setMaxConcurrentConsumers(5);
        factory.setPrefetchCount(1);
        return factory;
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter()); // ← 强制生产者用 JSON
        return template;
    }
}
