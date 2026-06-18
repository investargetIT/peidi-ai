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
    /**
     * Declares the DirectExchange used as the PDF dead-letter exchange.
     *
     * @return the DirectExchange instance named by {@code PDF_DLX}
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(PDF_DLX);
    }

    /**
     * Declares the durable PDF processing queue configured with dead-letter routing.
     *
     * The queue is created with its dead-letter exchange set to {@code PDF_DLX} and
     * its dead-letter routing key set to {@code PDF_DLQ_ROUTING_KEY}.
     *
     * @return the durable {@code Queue} named {@code PDF_PROCESS_QUEUE} with dead-letter settings
     */
    @Bean
    public Queue pdfProcessQueue() {
        return QueueBuilder.durable(PDF_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", PDF_DLX)  //
                .withArgument("x-dead-letter-routing-key", PDF_DLQ_ROUTING_KEY)  //
                .build();
    }

    /**
     * Declares a durable RabbitMQ queue used as the PDF dead-letter queue.
     *
     * @return the durable Queue representing the PDF dead-letter queue
     */
    @Bean
    public Queue pdfDlq() {
        return QueueBuilder.durable(PDF_DLQ).build();
    }

    /**
     * Bind the PDF dead-letter queue to the PDF dead-letter exchange using the configured DLQ routing key.
     *
     * @return the binding that connects the PDF DLQ to the PDF DLX with the configured routing key
     */
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(pdfDlq())
                .to(deadLetterExchange())
                .with(PDF_DLQ_ROUTING_KEY);  //与主队列的 routing-key 一致
    }

    /**
     * Creates a SimpleRabbitListenerContainerFactory configured for PDF consumers.
     *
     * The factory uses the JSON message converter, sets concurrency to 5 (max 5),
     * prefetch count to 1, manual acknowledge mode, and does not requeue rejected messages.
     *
     * @return a SimpleRabbitListenerContainerFactory configured for PDF processing
     */
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
    /**
     * Declares the durable DRAW process queue configured to route dead letters to the DRAW dead-letter queue.
     *
     * @return the durable Queue named DRAW_PROCESS_QUEUE with its dead-letter exchange set to the default exchange
     *         and dead-letter routing key set to DRAW_DLQ
     */
    @Bean
    public Queue drawProcessQueue() {
        return QueueBuilder.durable(DRAW_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DRAW_DLQ)
                .build();
    }

    /**
     * Declares a durable dead-letter queue for DRAW processing failures.
     *
     * @return the durable Queue named DRAW_DLQ used to store messages routed to the draw dead-letter queue
     */
    @Bean
    public Queue drawDlq() {
        return QueueBuilder.durable(DRAW_DLQ).build();
    }

    /**
     * Create a listener container factory configured for DRAW consumers.
     *
     * <p>The factory uses JSON message conversion, a connection factory provided by the caller,
     * 3 concurrent consumers (max 3), and a prefetch count of 1.</p>
     *
     * @return a configured SimpleRabbitListenerContainerFactory for DRAW processing
     */
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
    /**
     * Declares the durable WECOM processing queue and configures its dead-letter routing.
     *
     * The queue is durable and has dead-letter arguments that route rejected messages to the
     * `DRAW_DLQ` queue via the default (empty) exchange.
     *
     * @return the durable `Queue` configured for WECOM processing with dead-letter routing to `DRAW_DLQ`
     */
    @Bean
    public Queue wecomProcessQueue() {
        return QueueBuilder.durable(WECOM_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DRAW_DLQ)
                .build();
    }

    /**
     * Declares a durable dead-letter queue for WeCom message processing.
     *
     * @return the durable WeCom dead-letter {@code Queue}
     */
    @Bean
    public Queue wecomDlq() {
        return QueueBuilder.durable(WECOM_DLQ).build();
    }

    /**
     * Creates a listener container factory configured for WECOM message consumers.
     *
     * The returned factory uses the JSON message converter, is wired to the provided
     * ConnectionFactory, starts with 3 concurrent consumers (max 3) and a prefetch
     * count of 1.
     *
     * @return a configured SimpleRabbitListenerContainerFactory for WECOM processing
     */
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
    /**
     * Declares the KEFU processing queue configured to route dead-lettered messages to DRAW_DLQ.
     *
     * @return the durable Queue named by KEFU_PROCESS_QUEUE with
     *         `x-dead-letter-exchange` set to "" (default exchange) and
     *         `x-dead-letter-routing-key` set to DRAW_DLQ
     */
    @Bean
    public Queue kefuProcessQueue() {
        return QueueBuilder.durable(KEFU_PROCESS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DRAW_DLQ)
                .build();
    }

    /**
     * Declares a durable dead-letter queue for KEFU messages.
     *
     * @return the durable Queue instance identified by {@code KEFU_DLQ} used to store failed KEFU messages
     */
    @Bean
    public Queue kefuDlq() {
        return QueueBuilder.durable(KEFU_DLQ).build();
    }

    /**
     * Create a SimpleRabbitListenerContainerFactory configured for KEFU consumers.
     *
     * <p>The factory uses the Jackson JSON message converter, the provided connection factory,
     * 5 concurrent consumers (max 5), and a prefetch count of 1.
     *
     * @param connectionFactory the RabbitMQ connection factory to assign to the listener container factory
     * @return a configured SimpleRabbitListenerContainerFactory for KEFU listeners
     */
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
