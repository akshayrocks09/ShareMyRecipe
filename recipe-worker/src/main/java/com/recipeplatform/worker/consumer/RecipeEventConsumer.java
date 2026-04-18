package com.recipeplatform.worker.consumer;

import com.rabbitmq.client.Channel;
import com.recipeplatform.worker.config.RabbitMQConfig;
import com.recipeplatform.worker.dto.RecipeEventMessage;
import com.recipeplatform.worker.service.RecipeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Three listeners — one per queue — all backed by the same ingestion service.
 * Manual ACK ensures messages are not lost if the worker crashes mid-processing.
 * On failure after retries, the message is nack'd without requeue → DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeEventConsumer {

    private final RecipeIngestionService ingestionService;

    // ── recipe.published ─────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PUBLISH,
                    containerFactory = "rabbitListenerContainerFactory")
    public void onPublished(RecipeEventMessage msg, Message amqpMessage, Channel channel)
            throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        log.info("Received PUBLISHED event for recipe {}", msg.getRecipeId());

        try {
            ingestionService.handlePublished(msg);
            channel.basicAck(deliveryTag, false);
            log.info("ACK'd PUBLISHED event for recipe {}", msg.getRecipeId());
        } catch (Exception e) {
            log.error("Failed to process PUBLISHED event for recipe {}: {}",
                    msg.getRecipeId(), e.getMessage(), e);
            // false, false → don't requeue; message goes to DLQ
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // ── recipe.updated ───────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.QUEUE_UPDATE,
                    containerFactory = "rabbitListenerContainerFactory")
    public void onUpdated(RecipeEventMessage msg, Message amqpMessage, Channel channel)
            throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        log.info("Received UPDATED event for recipe {}", msg.getRecipeId());

        try {
            ingestionService.handleUpdated(msg);
            channel.basicAck(deliveryTag, false);
            log.info("ACK'd UPDATED event for recipe {}", msg.getRecipeId());
        } catch (Exception e) {
            log.error("Failed to process UPDATED event for recipe {}: {}",
                    msg.getRecipeId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    // ── recipe.deleted ───────────────────────────────────────────────────────

    @RabbitListener(queues = RabbitMQConfig.QUEUE_DELETE,
                    containerFactory = "rabbitListenerContainerFactory")
    public void onDeleted(RecipeEventMessage msg, Message amqpMessage, Channel channel)
            throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        log.info("Received DELETED event for recipe {}", msg.getRecipeId());

        try {
            ingestionService.handleDeleted(msg);
            channel.basicAck(deliveryTag, false);
            log.info("ACK'd DELETED event for recipe {}", msg.getRecipeId());
        } catch (Exception e) {
            log.error("Failed to process DELETED event for recipe {}: {}",
                    msg.getRecipeId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
