package tn.isetkebili.transaction.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tn.isetkebili.transaction.dto.TransferRequest;
import tn.isetkebili.transaction.entity.Transaction;
import tn.isetkebili.transaction.entity.TransactionStatus;
import tn.isetkebili.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final RestClient accountRestClient;

    public TransactionService(TransactionRepository repository, RestClient accountRestClient) {
        this.repository = repository;
        this.accountRestClient = accountRestClient;
    }

    // 1. Le client demande un virement (on ne touche pas à l'argent tout de suite)
    public Transaction transfer(TransferRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant doit être positif");
        }

        Transaction tx = new Transaction();
        tx.setSourceAccountId(request.getSourceAccountId());
        tx.setDestinationAccountId(request.getDestinationAccountId());
        tx.setAmount(request.getAmount());
        tx.setStatus(TransactionStatus.PENDING); // On met PENDING !
        return repository.save(tx);
    }

    // 2. L'admin approuve le virement (c'est ici que l'argent bouge)
    public Transaction approveTransaction(Long id) {
        Transaction tx = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction introuvable"));

        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new IllegalStateException("Cette transaction n'est pas en attente");
        }

        try {
            // Appel au Account Service pour débiter
            accountRestClient.post()
                    .uri("/api/accounts/{id}/debit", tx.getSourceAccountId())
                    .body(tx.getAmount())
                    .retrieve()
                    .toBodilessEntity();

            // Appel au Account Service pour créditer
            accountRestClient.post()
                    .uri("/api/accounts/{id}/credit", tx.getDestinationAccountId())
                    .body(tx.getAmount())
                    .retrieve()
                    .toBodilessEntity();

            tx.setStatus(TransactionStatus.COMPLETED);
            return repository.save(tx);

        } catch (Exception e) {
            tx.setStatus(TransactionStatus.FAILED);
            repository.save(tx);
            throw new RuntimeException("Échec du virement : " + e.getMessage());
        }
    }

    public List<Transaction> findAll() {
        return repository.findAll();
    }
}