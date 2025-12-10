package com.jpmc.midascore.service;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class TransactionService {

    private final UserRepository userRepository;
    private final TransactionRecordRepository txRecordRepository;
    private final RestTemplate restTemplate;

    // incentive API URL (as given)
    private final String incentiveUrl = "http://localhost:8080/incentive";

    public TransactionService(UserRepository userRepository,
                              TransactionRecordRepository txRecordRepository,
                              RestTemplate restTemplate) {
        this.userRepository = userRepository;
        this.txRecordRepository = txRecordRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public boolean validateAndRecord(Transaction tx) {
        UserRecord sender = userRepository.findById(tx.getSenderId());
        UserRecord recipient = userRepository.findById(tx.getRecipientId());

        // validation
        if (sender == null || recipient == null) {
            return false;
        }
        if (sender.getBalance() < tx.getAmount()) {
            return false;
        }

        // deduct from sender
        sender.setBalance(sender.getBalance() - tx.getAmount());
        userRepository.save(sender);

        // call incentives API (may return Incentive with amount >= 0)
        float incentiveAmount = 0f;
        try {
            Incentive incentive = restTemplate.postForObject(incentiveUrl, tx, Incentive.class);
            if (incentive != null) {
                incentiveAmount = incentive.getAmount();
            }
        } catch (Exception e) {
            // If incentive service is down or fails, treat incentive as 0
            // log and continue (do not fail the transaction)
            // use System.err or proper logger (logger not included here to keep things simple)
            System.err.println("Failed to call incentive API: " + e.getMessage());
            incentiveAmount = 0f;
        }

        // add amount + incentive to recipient
        recipient.setBalance(recipient.getBalance() + tx.getAmount() + incentiveAmount);
        userRepository.save(recipient);

        // save transaction record with incentive
        TransactionRecord record = new TransactionRecord(sender, recipient, tx.getAmount(), incentiveAmount);
        txRecordRepository.save(record);

        return true;
    }
}
