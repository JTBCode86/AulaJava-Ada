package com.melicore.engine.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String ORDERS_EVENTS_TOPIC = "orders.events";
    public static final String ORDERS_DLQ_TOPIC = "orders.dlq";

    @Bean
    public NewTopic ordersEventsTopic() {
        return TopicBuilder.name(ORDERS_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ordersDlqTopic() {
        return TopicBuilder.name(ORDERS_DLQ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}