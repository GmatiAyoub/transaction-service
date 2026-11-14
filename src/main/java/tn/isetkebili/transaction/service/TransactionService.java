package tn.isetkebili.transaction.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tn.isetkebili.transaction.dto.TransferRequest;
import tn.isetkebili.transaction.entity.Transaction;
import tn.isetkebili.transaction.entity.TransactionStatus;
import tn.isetkebili.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final RestClient accountRestClient;
    private final RestClient notificationRestClient; // Nouveau client

    public TransactionService(TransactionRepository repository,
                              RestClient accountRestClient,
                              @Value("${services.notification.url:http://notification-service:8084}") String notificationUrl) {
        this.repository = repository;
        this.accountRestClient = accountRestClient;
        this.notificationRestClient = RestClient.builder().baseUrl(notificationUrl).build();
    }

    public Transaction transfer(TransferRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant doit être positif");
        }

        Transaction tx = new Transaction();
        tx.setSourceAccountId(request.getSourceAccountId());
        tx.setDestinationAccountId(request.getDestinationAccountId());
        tx.setAmount(request.getAmount());
        tx.setStatus(TransactionStatus.PENDING);
        repository.save(tx);

        try {
            // Débit
            accountRestClient.post()
                    .uri("/api/accounts/{id}/debit", request.getSourceAccountId())
                    .body(request.getAmount())
                    .retrieve()
                    .toBodilessEntity();

            // Crédit
            accountRestClient.post()
                    .uri("/api/accounts/{id}/credit", request.getDestinationAccountId())
                    .body(request.getAmount())
                    .retrieve()
                    .toBodilessEntity();

            tx.setStatus(TransactionStatus.COMPLETED);
            Transaction savedTx = repository.save(tx);

            // ⚠️ NOUVEAU : Appel au Notification Service
            try {
                Map<String, Object> notification = Map.of(
                        "customerId", 1, // On pourrait récupérer le vrai customerId
                        "message", "Virement de " + request.getAmount() + " TND effectué avec succès.",
                        "type", "TRANSFER_COMPLETED"
                );
                notificationRestClient.post()
                        .uri("/api/notifications")
                        .body(notification)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                // Si la notification échoue, on ne bloque pas le virement
                System.err.println("Erreur d'envoi de notification : " + e.getMessage());
            }

            return savedTx;

        } catch (Exception e) {
            tx.setStatus(TransactionStatus.FAILED);
            repository.save(tx);
            throw new RuntimeException("Échec du virement : " + e.getMessage());
        }
    }

    public List<Transaction> findAll() { return repository.findAll(); }
}