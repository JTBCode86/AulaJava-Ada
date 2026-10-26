package com.ada.sagacqrs.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    public static final String ORDER_CREATED_TOPIC = "order-created-topic";
    public static final String ORDER_PAID_TOPIC = "order-paid-topic";
    public static final String ORDER_CREATED_DLQ = "order-created-topic.DLQ";

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(ORDER_CREATED_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderPaidTopic() {
        return TopicBuilder.name(ORDER_PAID_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCreatedDlqTopic() {
        return TopicBuilder.name(ORDER_CREATED_DLQ).partitions(3).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        // Envia para o tópico de DLQ com sufixo .DLQ após falhas
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        // Tenta 3 vezes com intervalo de 1 segundo entre tentativas
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}