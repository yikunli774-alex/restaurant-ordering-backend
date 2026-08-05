package io.github.yikunli774.ordering.benchmark;

import io.github.yikunli774.ordering.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * NOT part of the normal test suite (no "Test" suffix → Surefire skips it).
 * Run on demand:  ./mvnw test -Dtest=InventoryBenchmark
 *
 * Fires many concurrent reservations against ONE stock row and compares:
 *   - baseline : naive read-modify-write (SELECT, check, UPDATE) — a typical first attempt
 *   - corrected: conditional UPDATE (compare-and-swap) — the exact technique
 *                InventoryRepository.reserve() uses in production
 * against a constraint-free scratch table, so the naive oversell is visible as
 * negative stock (the real `inventory` table additionally has a CHECK >= 0 net).
 */
class InventoryBenchmark extends AbstractIntegrationTest {

    private static final int CONCURRENCY = 1000;
    private static final int CAPACITY = 100;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void reservationBenchmark() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS bench_stock (id INT PRIMARY KEY, available INT NOT NULL)");

        resetStock();
        Result naive = run(this::naiveReserve);
        int naiveLeft = available();

        resetStock();
        Result corrected = run(this::casReserve);
        int correctedLeft = available();

        System.out.println();
        System.out.println("============ 库存并发预留 Benchmark ============");
        System.out.printf("场景: %d 个并发请求争抢 %d 份库存 (Java 21 虚拟线程, 真 MySQL)%n", CONCURRENCY, CAPACITY);
        System.out.println();
        System.out.println("【朴素版: 读→判断→写 (典型新手写法, 有竞态)】");
        System.out.printf("  自认为下单成功: %d 份%n", naive.successes);
        System.out.printf("  超卖(卖出 - 库存): %d 份%n", Math.max(0, naive.successes - CAPACITY));
        System.out.printf("  最终库存: %d   %s%n", naiveLeft, naiveLeft < 0 ? "← 负数! 数据已被压坏" : "");
        System.out.println();
        System.out.println("【修正版: 条件更新 CAS (本项目 InventoryRepository.reserve 的做法)】");
        System.out.printf("  下单成功: %d 份   (恰好 = 库存)%n", corrected.successes);
        System.out.printf("  超卖: %d 份%n", Math.max(0, corrected.successes - CAPACITY));
        System.out.printf("  最终库存: %d%n", correctedLeft);
        System.out.printf("  吞吐: %.0f 次/秒%n", CONCURRENCY / (corrected.wallNanos / 1e9));
        System.out.printf("  延迟 P50 / P95 / P99: %.1f / %.1f / %.1f ms%n",
                pctMs(corrected, 0.50), pctMs(corrected, 0.95), pctMs(corrected, 0.99));
        System.out.println("=================================================");
        System.out.println();
    }

    /** Naive: SELECT then UPDATE — many threads read the same value and all decrement. */
    private boolean naiveReserve() {
        Integer available = jdbc.queryForObject("SELECT available FROM bench_stock WHERE id = 1", Integer.class);
        if (available != null && available >= 1) {
            jdbc.update("UPDATE bench_stock SET available = available - 1 WHERE id = 1");
            return true;
        }
        return false;
    }

    /** Corrected: one conditional statement — the DB row lock lets only enough winners through. */
    private boolean casReserve() {
        return jdbc.update("UPDATE bench_stock SET available = available - 1 WHERE id = 1 AND available >= 1") > 0;
    }

    private record Result(int successes, long[] latenciesNanos, long wallNanos) {
    }

    private Result run(BooleanSupplier op) throws Exception {
        AtomicInteger successes = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>(CONCURRENCY);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CONCURRENCY; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    long t0 = System.nanoTime();
                    boolean ok = op.getAsBoolean();
                    long elapsed = System.nanoTime() - t0;
                    if (ok) {
                        successes.incrementAndGet();
                    }
                    return elapsed;
                }));
            }
            long wall0 = System.nanoTime();
            start.countDown();
            long[] latencies = new long[CONCURRENCY];
            for (int i = 0; i < CONCURRENCY; i++) {
                latencies[i] = futures.get(i).get();
            }
            return new Result(successes.get(), latencies, System.nanoTime() - wall0);
        }
    }

    private static double pctMs(Result r, double p) {
        long[] sorted = r.latenciesNanos().clone();
        java.util.Arrays.sort(sorted);
        return sorted[(int) (p * (sorted.length - 1))] / 1e6;
    }

    private void resetStock() {
        jdbc.update("DELETE FROM bench_stock");
        jdbc.update("INSERT INTO bench_stock (id, available) VALUES (1, ?)", CAPACITY);
    }

    private int available() {
        return jdbc.queryForObject("SELECT available FROM bench_stock WHERE id = 1", Integer.class);
    }
}
