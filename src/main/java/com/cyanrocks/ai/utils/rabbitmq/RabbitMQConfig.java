package com.cyanrocks.ai.utils.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.listener.FatalExceptionStrategy;
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
    public static final String PDF_DLX = "pdf.dlx";
    public static final String PDF_DLQ_ROUTING_KEY = "pdf.dlq.routing";

    public static final String DRAW_PROCESS_QUEUE = "draw.process.queue";
    public static final String DRAW_DLQ = "draw.process.dlq";

    public static final String WECOM_PROCESS_QUEUE = "wecom.process.queue";
    public static final String WECOM_DLQ = "wecom.process.dlq";

    public static final String KEFU_PROCESS_QUEUE = "kefu.process.queue";
    public static final String KEFU_DLQ = "kefu.process.dlq";
    //PDF
    // 死信交换机
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(PDF_DLX);
    }

    // 主队列（带死信配置）
    @Bean
    public Queue pdfProcessQueue() {
        return QueueBuilder.durable(PDF_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", PDF_DLX)  //
                .withArgument("x-dead-letter-routing-key", PDF_DLQ_ROUTING_KEY)  //
                .build();
    }

    // 死信队列
    @Bean
    public Queue pdfDlq() {
        return QueueBuilder.durable(PDF_DLQ).build();
    }

    // 死信队列绑定
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(pdfDlq())
                .to(deadLetterExchange())
                .with(PDF_DLQ_ROUTING_KEY);  //与主队列的 routing-key 一致
    }

    // 容器工厂配置
    @Bean("pdfContainerFactory")
    public SimpleRabbitListenerContainerFactory pdfContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(5);
        factory.setMaxConcurrentConsumers(5);
        factory.setPrefetchCount(1);
        // 手动确认模式
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // 重试耗尽后不重新入队
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    //DRAW
    // 主队列（持久化 + 死信路由）
    @Bean
    public Queue drawProcessQueue() {
        return QueueBuilder.durable(DRAW_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DRAW_DLQ)
                .build();
    }

    // 死信队列（用于存储处理失败的消息）
    @Bean
    public Queue drawDlq() {
        return QueueBuilder.durable(DRAW_DLQ).build();
    }

    @Bean("drawContainerFactory")
    public SimpleRabbitListenerContainerFactory drawContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(3);
        factory.setPrefetchCount(1);
        return factory;
    }

    //WECOM
    // 主队列（持久化 + 死信路由）
    @Bean
    public Queue wecomProcessQueue() {
        return QueueBuilder.durable(WECOM_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DRAW_DLQ)
                .build();
    }

    // 死信队列（用于存储处理失败的消息）
    @Bean
    public Queue wecomDlq() {
        return QueueBuilder.durable(WECOM_DLQ).build();
    }

    @Bean("wecomContainerFactory")
    public SimpleRabbitListenerContainerFactory wecomContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(3);
        factory.setPrefetchCount(1);
        return factory;
    }

    //KEFU
    // 主队列（持久化 + 死信路由）
    @Bean
    public Queue kefuProcessQueue() {
        return QueueBuilder.durable(KEFU_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DRAW_DLQ)
                .build();
    }

    // 死信队列（用于存储处理失败的消息）
    @Bean
    public Queue kefuDlq() {
        return QueueBuilder.durable(KEFU_DLQ).build();
    }

    @Bean("kefuContainerFactory")
    public SimpleRabbitListenerContainerFactory kefuContainerFactory(ConnectionFactory connectionFactory) {
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
