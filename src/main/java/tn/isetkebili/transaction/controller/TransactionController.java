package tn.isetkebili.transaction.controller;

import org.springframework.web.bind.annotation.*;
import tn.isetkebili.transaction.entity.Transaction;
import tn.isetkebili.transaction.service.TransactionService;
import java.util.List;
import tn.isetkebili.transaction.dto.TransferRequest;
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping("/transfer")
    public Transaction transfer(@RequestBody TransferRequest request) {
        return service.transfer(request);
    }

    @GetMapping
    public List<Transaction> findAll() {
        return service.findAll();
    }
}