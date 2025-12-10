package com.jpmc.midascore.service;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final UserRepository userRepository;
    private final TransactionRecordRepository txRepository;

    public TransactionService(UserRepository userRepository, TransactionRecordRepository txRepository) {
        this.userRepository = userRepository;
        this.txRepository = txRepository;
    }

    @Transactional
    public boolean validateAndRecord(Transaction tx) {
        if (tx == null) return false;

        long senderId = tx.getSenderId();
        long recipientId = tx.getRecipientId();
        float amount = tx.getAmount();

        if (amount <= 0f) {
            log.info("Dropping transaction with non-positive amount: {}", amount);
            return false;
        }

        // your UserRepository defines findById(long) that returns UserRecord (not Optional)
        UserRecord sender = null;
        UserRecord recipient = null;
        try {
            sender = userRepository.findById(senderId);
        } catch (Exception ignored) {}

        try {
            recipient = userRepository.findById(recipientId);
        } catch (Exception ignored) {}

        if (sender == null || recipient == null) {
            log.info("Sender or recipient missing - sender: {}, recipient: {}", senderId, recipientId);
            return false;
        }

        // validate sender balance
        if (sender.getBalance() < amount) {
            log.info("Insufficient funds for sender {}: balance={}, required={}", senderId, sender.getBalance(), amount);
            return false;
        }

        // perform updates
        sender.setBalance(sender.getBalance() - amount);
        recipient.setBalance(recipient.getBalance() + amount);

        // persist updated users (save will work with CrudRepository)
        userRepository.save(sender);
        userRepository.save(recipient);

        // persist transaction record
        TransactionRecord record = new TransactionRecord(sender, recipient, amount);
        txRepository.save(record);

        log.debug("Recorded transaction: {} -> {} amount {}", senderId, recipientId, amount);
        return true;
    }
}
