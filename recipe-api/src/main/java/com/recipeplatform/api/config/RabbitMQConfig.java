package com.recipeplatform.api.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.host")
public class RabbitMQConfig {

    // Exchange
    public static final String RECIPE_EXCHANGE = "recipe.events";

    // Routing keys
    public static final String ROUTING_PUBLISHED  = "recipe.published";
    public static final String ROUTING_UPDATED    = "recipe.updated";
    public static final String ROUTING_DELETED    = "recipe.deleted";

    // Queue names (consumed by the worker)
    public static final String QUEUE_PUBLISH = "recipe.publish.queue";
    public static final String QUEUE_UPDATE  = "recipe.update.queue";
    public static final String QUEUE_DELETE  = "recipe.delete.queue";

    // Dead-letter exchange / queue
    public static final String DLX_EXCHANGE = "recipe.events.dlx";
    public static final String DLQ_QUEUE    = "recipe.events.dlq";

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
    public Binding publishBinding(@Qualifier("publishQueue") Queue publishQueue, TopicExchange recipeExchange) {
        return BindingBuilder.bind(publishQueue).to(recipeExchange).with(ROUTING_PUBLISHED);
    }

    @Bean
    public Binding updateBinding(@Qualifier("updateQueue") Queue updateQueue, TopicExchange recipeExchange) {
        return BindingBuilder.bind(updateQueue).to(recipeExchange).with(ROUTING_UPDATED);
    }

    @Bean
    public Binding deleteBinding(@Qualifier("deleteQueue") Queue deleteQueue, TopicExchange recipeExchange) {
        return BindingBuilder.bind(deleteQueue).to(recipeExchange).with(ROUTING_DELETED);
    }

    @Bean
    public Binding deadLetterBinding(@Qualifier("deadLetterQueue") Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_QUEUE);
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
}
