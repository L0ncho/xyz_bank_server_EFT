package cl.duoc.xyzbank.coreservice.interests.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "interests.kafka.enabled", havingValue = "true")
public class KafkaInterestsConfig {

    @Bean
    public NewTopic interestsCalculatedTopic(@Value("${interests.kafka.calculated-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic interestsCreditResultsTopic(@Value("${interests.kafka.credit-results-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic interestsCalculatedDeadLetterTopic(@Value("${interests.kafka.calculated-topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public CommonErrorHandler interestCalculatedErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${interests.kafka.listener-retry-interval-ms:1000}") long retryIntervalMs,
            @Value("${interests.kafka.listener-max-retries:3}") long maxRetries) {
        return new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate, (record, exception) ->
                        new TopicPartition(record.topic() + ".DLT", record.partition())),
                new FixedBackOff(retryIntervalMs, maxRetries));
    }
}
