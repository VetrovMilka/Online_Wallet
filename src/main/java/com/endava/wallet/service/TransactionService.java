package com.endava.wallet.service;

import com.endava.wallet.entity.CircleStatistics;
import com.endava.wallet.entity.Profile;
import com.endava.wallet.entity.Transaction;
import com.endava.wallet.entity.User;
import com.endava.wallet.exception.ApiRequestException;
import com.endava.wallet.repository.TransactionRepository;
import com.endava.wallet.repository.TransactionsCategoryRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

@Service
@AllArgsConstructor
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    private TransactionRepository transactionRepository;
    private TransactionsCategoryRepository categoryRepository;
    private ProfileService profileService;

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    public LocalDate parseDate(String transactionDate) {
        return LocalDate.parse(transactionDate);
    }

    public void add(Transaction transaction, Profile profile) {
        transactionRepository.save(transaction);

        if (Boolean.TRUE.equals(transaction.getIsIncome())) {
            profile.setBalance(profile.getBalance().add(transaction.getAmount()));
        } else {
            profile.setBalance(profile.getBalance().subtract(transaction.getAmount()));
        }
        profileService.save(profile);
        logger.error("Transaction with id: {} was added", transaction.getId());
    }

    public void save(User user, Long id, String message, String category, BigDecimal amount, String transactionDate) {
        Profile currentProfile = profileService.findProfileByUser(user);
        Transaction transaction = findTransactionByIdAndProfile(id, currentProfile);

        if (amount != null && !amount.equals(transaction.getAmount())) {
            if (Boolean.TRUE.equals(transaction.getIsIncome())) {
                currentProfile.setBalance(currentProfile.getBalance().subtract(transaction.getAmount()));
                currentProfile.setBalance(currentProfile.getBalance().add(amount));
            } else {
                currentProfile.setBalance(currentProfile.getBalance().add(transaction.getAmount()));
                currentProfile.setBalance(currentProfile.getBalance().subtract(amount));
            }
        }

        if (amount != null) {
            transaction.setAmount(amount);
        }
        transaction.setCategory(categoryRepository.findByCategory(category));
        transaction.setTransactionDate(parseDate(transactionDate));
        transaction.setMessage(message);

        transactionRepository.save(transaction);
        profileService.save(currentProfile);
    }

    public Transaction findTransactionById(Long id) {
        if (transactionRepository.findTransactionById(id) == null) {
            logger.error("Transaction with id: {} not found", id);
            throw new ApiRequestException("Transaction with id: " + id + " not found");
        }
        return transactionRepository.findTransactionById(id);
    }

    public Transaction findTransactionByIdAndProfile(Long id, Profile profile) {
        if (transactionRepository.findTransactionById(id).getProfile() != profile) {
            logger.error("Transaction with id: {} not found", id);
            throw new ApiRequestException("Transaction with id: " + id + " not found");
        }
        return transactionRepository.findTransactionById(id);
    }

    public List<Transaction> findRecentTransactionsByUser(User user) {
        Profile profile = profileService.findProfileByUser(user);
        return findRecentTransactionsByProfile(profile);
    }

    public List<Transaction> findRecentTransactionsByProfile(Profile profile) {
        List<Transaction> transactions = transactionRepository.findTransactionByProfileOrderByTransactionDateAsc(profile);
        Collections.reverse(transactions);
        return transactions;
    }

    public BigDecimal findTranSumDateBetween(Profile profile, boolean isIncome, LocalDate from, LocalDate to) {
        List<Transaction> transactions = transactionRepository.findByProfileAndIsIncomeAndTransactionDateBetween(
                profile,
                isIncome,
                from,
                to);

        return transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Pair<String, BigDecimal> findMaxCategorySumDateBetween(
            Profile profile,
            boolean isIncome,
            LocalDate from,
            LocalDate to
    ) {
        String maxTranCategory = transactionRepository.findMaxCategoryDateBetween(
                profile,
                isIncome,
                from,
                to
        );
        if (maxTranCategory == null) maxTranCategory = "nothing";

        BigDecimal maxTranSum = transactionRepository.findMaxSumDateBetween(
                profile,
                isIncome,
                from,
                to
        );
        if (maxTranSum == null) maxTranSum = BigDecimal.ZERO;

        return Pair.of(maxTranCategory, maxTranSum);
    }

    public CircleStatistics findCategoryAndSumByProfileAndIsIncome(Profile profile, Boolean isIncome) {
        return new CircleStatistics(
                transactionRepository.findCategoryByProfileAndIsIncome(profile, isIncome),
                transactionRepository.findCategorySumByProfileAndIsIncome(profile, isIncome)
        );
    }

    public List<LocalDate> findTransactionsDates(Profile profile) {
        return profile.getTransactions().stream()
                .map(transaction -> transaction.getTransactionDate().withDayOfMonth(1))
                .filter(distinctByKey(LocalDate::getMonth))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public void deleteTransactionById(Long transactionID, User user) {
        Profile profile = profileService.findProfileByUser(user);
        findTransactionByIdAndProfile(transactionID, profile);
        Transaction transaction = transactionRepository.findTransactionById(transactionID);
        transactionRepository.deleteById(transactionID);
        if (Boolean.TRUE.equals(transaction.getIsIncome())) {
            profile.setBalance(profile.getBalance().subtract(transaction.getAmount()));
        } else {
            profile.setBalance(profile.getBalance().add(transaction.getAmount()));
        }
        profileService.save(profile);

    }
}
