package com.iCanteen.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class CacheConsistencyHelper {

    private static final ExecutorService DELAYED_DELETE_EXECUTOR = Executors.newSingleThreadExecutor();

    private final StringRedisTemplate stringRedisTemplate;

    public CacheConsistencyHelper(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void runAfterCommit(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
            return;
        }
        runnable.run();
    }

    public void deleteAfterCommit(String... keys) {
        String[] validKeys = Arrays.stream(keys)
                .filter(k -> k != null && !k.trim().isEmpty())
                .toArray(String[]::new);
        if (validKeys.length == 0) {
            return;
        }
        runAfterCommit(() -> deleteNowAndDelayed(validKeys));
    }

    private void deleteNowAndDelayed(String[] keys) {
        stringRedisTemplate.delete(Arrays.asList(keys));
        DELAYED_DELETE_EXECUTOR.submit(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(200);
                stringRedisTemplate.delete(Arrays.asList(keys));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
