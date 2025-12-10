package com.jpmc.midascore;

import com.jpmc.midascore.foundation.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class KafkaTransactionListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaTransactionListener.class);

    // Thread-safe list for test verification (store amounts in arrival order)
    public static final List<Float> receivedAmounts = new CopyOnWriteArrayList<>();

    // Accept Transaction directly — Spring Kafka will deserialize into this type
    @KafkaListener(topics = "${general.kafka-topic:midas-topic}")
    public void listen(Transaction tx) {
        try {
            if (tx == null) {
                log.warn("Received null Transaction payload");
                return;
            }

            float amount = tx.getAmount();
            receivedAmounts.add(amount);

            log.info(
                    "KafkaTransactionListener received transaction - sender: {}, recipient: {}, amount: {}",
                    tx.getSenderId(),
                    tx.getRecipientId(),
                    amount
            );

            // Safety: keep list bounded (not required for tests)
            if (receivedAmounts.size() > 1000) {
                while (receivedAmounts.size() > 1000) {
                    receivedAmounts.remove(0);
                }
            }
        } catch (Exception e) {
            log.error("Error handling incoming Transaction: {}", tx, e);
        }
    }
}
