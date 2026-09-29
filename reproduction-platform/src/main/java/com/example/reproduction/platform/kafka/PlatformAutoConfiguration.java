package com.example.reproduction.platform.kafka;

import com.example.reproduction.messaging.Topics;
import com.example.reproduction.platform.logging.CorrelationIdFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consumer-side failure policy shared by every service:
 * transient failure -> in-process exponential back-off (3 retries) -> dead-letter topic {@code reproduction.dlq}.
 * Deserialization failures (poison messages) and validation failures are NOT retried: straight to the DLQ.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
public class PlatformAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(PlatformAutoConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KafkaTemplate.class)
    static class KafkaFailurePolicy {

        /** Dead letters may carry raw bytes (undeserializable input) or typed objects, so serialize by runtime type. */
        /**
         * NOT a bean on purpose: Spring Boot's own KafkaTemplate backs off when any KafkaTemplate bean exists.
         */
        private static KafkaTemplate<Object, Object> dlqKafkaTemplate(KafkaProperties props) {
            Map<Class<?>, Serializer<?>> keys = new LinkedHashMap<>();
            keys.put(byte[].class, new ByteArraySerializer());
            keys.put(String.class, new StringSerializer());
            Map<Class<?>, Serializer<?>> values = new LinkedHashMap<>(keys);
            JsonSerializer<Object> json = new JsonSerializer<>();
            json.setAddTypeInfo(false);
            values.put(Object.class, json); // last: fallback for typed payloads
            DefaultKafkaProducerFactory<Object, Object> pf = new DefaultKafkaProducerFactory<>(
                    props.buildProducerProperties(null), new DelegatingByTypeSerializer(keys, true), new DelegatingByTypeSerializer(values, true));
            return new KafkaTemplate<>(pf);
        }

        @Bean
        CommonErrorHandler kafkaErrorHandler(KafkaProperties props, MeterRegistry meters) {
            KafkaTemplate<Object, Object> dlqKafkaTemplate = dlqKafkaTemplate(props);
            Counter dlq = Counter.builder("dlq_messages").description("Messages sent to reproduction.dlq").register(meters);
            Counter retries = Counter.builder("kafka_retry_count").description("In-process consumer retries").register(meters);
            DeadLetterPublishingRecoverer publisher = new DeadLetterPublishingRecoverer(dlqKafkaTemplate,
                    (rec, ex) -> new TopicPartition(Topics.DLQ, -1));
            ExponentialBackOff backOff = new ExponentialBackOff(250, 2.0);
            backOff.setMaxAttempts(3);
            DefaultErrorHandler h = new DefaultErrorHandler((rec, ex) -> {
                dlq.increment();
                log.error("Sending record to DLQ topic={} partition={} offset={} cause={}", rec.topic(), rec.partition(), rec.offset(), ex.toString());
                publisher.accept(rec, ex);
            }, backOff);
            h.setRetryListeners((rec, ex, attempt) -> retries.increment());
            h.addNotRetryableExceptions(PoisonMessageException.class, IllegalArgumentException.class);
            return h;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class Web {
        @Bean
        FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
            FilterRegistrationBean<CorrelationIdFilter> r = new FilterRegistrationBean<>(new CorrelationIdFilter());
            r.setOrder(Integer.MIN_VALUE + 10);
            return r;
        }
    }
}
