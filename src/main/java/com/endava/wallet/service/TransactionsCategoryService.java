package com.endava.wallet.service;

import com.endava.wallet.entity.Transaction;
import com.endava.wallet.entity.TransactionsCategory;
import com.endava.wallet.exception.TransactionCategoryNotFoundException;
import com.endava.wallet.repository.TransactionsCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionsCategoryService {
    private final TransactionsCategoryRepository categoryRepository;

    private final TransactionService transactionService;

    public List<TransactionsCategory> findAllCategoriesByTransactionIdByIsIncome(Long transactionId) {

        Transaction transaction = transactionService.findTransactionById(transactionId);

        TransactionsCategory category = transaction.getCategory();

        return categoryRepository.findAll().stream()
                .filter(a -> a.getIsIncome().equals(category.getIsIncome()))
                .toList();
    }

    public TransactionsCategory findByCategory(String category) {
        return categoryRepository.findByCategory(category)
                .orElseThrow(() -> new TransactionCategoryNotFoundException("Transaction category not found!"));
    }

    public List<TransactionsCategory> findByIsIncome(boolean isIncome) {
        return categoryRepository.findByIsIncome(isIncome);
    }

    public List<TransactionsCategory> findAllCategoriesOrderByIsIncome() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.DESC, "isIncome"));
    }
}
