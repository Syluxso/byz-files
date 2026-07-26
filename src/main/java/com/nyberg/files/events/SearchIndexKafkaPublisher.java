package com.nyberg.files.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "byz.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class SearchIndexKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${byz.kafka.topics.search-index:byz.search.index}")
    private String topic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSearchIndex(SearchIndexApplicationEvent event) {
        SearchIndexEvent payload = event.getPayload();
        try {
            String json = objectMapper.writeValueAsString(payload);
            String key = payload.documentId();
            kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Failed to publish {} eventId={} documentId={}: {}",
                            payload.type(), payload.eventId(), payload.documentId(), ex.toString());
                } else {
                    log.info("Published {} eventId={} documentId={} topic={} partition={}",
                            payload.type(),
                            payload.eventId(),
                            payload.documentId(),
                            topic,
                            result.getRecordMetadata().partition());
                }
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize {} eventId={}: {}", payload.type(), payload.eventId(), e.toString());
        }
    }
}
