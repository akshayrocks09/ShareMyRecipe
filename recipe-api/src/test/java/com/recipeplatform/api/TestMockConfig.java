package com.recipeplatform.api;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/**
 * Shared test configuration that mocks external services
 * (RabbitMQ, Elasticsearch, S3) so integration tests only need H2.
 */
@TestConfiguration
public class TestMockConfig {

    @MockBean
    RabbitTemplate rabbitTemplate;

    @MockBean
    ElasticsearchOperations elasticsearchOperations;
}
