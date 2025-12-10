package com.jpmc.midascore;

import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.service.TransactionService;
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

    private final TransactionService transactionService;

    // Constructor injection - cleaner for testing and easier to reason about
    public KafkaTransactionListener(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

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

            // hand off to service which will validate and persist (or discard) the tx
            boolean accepted = transactionService.validateAndRecord(tx);
            if (!accepted) {
                log.info("Transaction discarded by validation: {}", tx);
            } else {
                log.debug("Transaction accepted and recorded: {}", tx);
            }

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
