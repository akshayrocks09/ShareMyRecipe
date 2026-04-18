package com.recipeplatform.worker.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String RECIPE_EXCHANGE    = "recipe.events";
    public static final String ROUTING_PUBLISHED  = "recipe.published";
    public static final String ROUTING_UPDATED    = "recipe.updated";
    public static final String ROUTING_DELETED    = "recipe.deleted";
    public static final String QUEUE_PUBLISH      = "recipe.publish.queue";
    public static final String QUEUE_UPDATE       = "recipe.update.queue";
    public static final String QUEUE_DELETE       = "recipe.delete.queue";
    public static final String DLX_EXCHANGE       = "recipe.events.dlx";
    public static final String DLQ_QUEUE          = "recipe.events.dlq";

    @Value("${app.worker.concurrency:3}")
    private int concurrency;

    @Bean
    public TopicExchange recipeExchange() {
        return ExchangeBuilder.topicExchange(RECIPE_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue publishQueue() {
        return QueueBuilder.durable(QUEUE_PUBLISH)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_QUEUE)
                .build();
    }

    @Bean
    public Queue updateQueue() {
        return QueueBuilder.durable(QUEUE_UPDATE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_QUEUE)
                .build();
    }

    @Bean
    public Queue deleteQueue() {
        return QueueBuilder.durable(QUEUE_DELETE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_QUEUE)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public Binding publishBinding() {
        return BindingBuilder.bind(publishQueue()).to(recipeExchange()).with(ROUTING_PUBLISHED);
    }

    @Bean
    public Binding updateBinding() {
        return BindingBuilder.bind(updateQueue()).to(recipeExchange()).with(ROUTING_UPDATED);
    }

    @Bean
    public Binding deleteBinding() {
        return BindingBuilder.bind(deleteQueue()).to(recipeExchange()).with(ROUTING_DELETED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency * 2);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false); // send failed messages to DLQ
        return factory;
    }
}
