# 并发压测:库存预留(超卖对比)

一句话:**同样 1000 个并发请求争抢 100 份库存,一个「典型新手写法」超卖 17 份、把库存压成负数;本项目的「条件更新 (CAS)」写法精确卖出 100 份、0 超卖。**

## 方法

- **机器**:本地开发机,Apple Silicon(arm64),11 核 / 18GB,macOS。
- **数据库**:真 MySQL 8.4(Testcontainers 起的容器,不是内存假库)。
- **并发**:Java 21 **虚拟线程**,1000 个任务同时释放(CountDownLatch),连接池 HikariCP。
- **争用**:所有请求抢**同一行库存**(最坏情况的「热点行」)。
- **对比的两种实现**(代码见 `src/test/.../benchmark/InventoryBenchmark.java`):
  - **朴素版**:`SELECT available` → 判断 `>= 1` → `UPDATE available = available - 1`。三步之间有时间窗,多个线程读到同一个值都以为「还有货」。
  - **修正版**:`UPDATE available = available - 1 WHERE available >= 1` 一条语句(**这就是生产代码 `InventoryRepository.reserve()` 用的招式**)。数据库对该行加锁、逐个串行执行,只有够库存的成功。
- 为看清「超卖」,对比跑在一张**无约束的临时表**上(真实的 `inventory` 表另有 `CHECK available >= 0` 作为第二层防线)。

## 结果(一次代表性运行)

| | 朴素版(读-判断-写) | 修正版(条件更新 CAS) |
|---|---|---|
| 自认为下单成功 | **117** 份 | **100** 份 |
| 超卖 | **17 份** | **0 份** |
| 最终库存 | **-17**(数据被压坏) | **0**(正确) |
| 吞吐 | — | **~7,577 次/秒** |
| 延迟 P50 / P95 / P99 | — | **99 / 123 / 126 ms** |

> 数字每次运行略有浮动(超卖量取决于线程交错),但结论是**确定性**的:朴素版一定 > 100(超卖),修正版一定 = 100(不超卖)。

## 复现

```bash
docker info >/dev/null   # 需要 Docker 在跑
./mvnw test -Dtest=InventoryBenchmark
```
(该类不带 `Test` 后缀,不会进入常规 `./mvnw test` 测试套件,只在显式指定时运行。)

## 简历用语

**中文**:为二维码点单后端实现并发安全的库存预留;用 1000 并发(Java 21 虚拟线程 + 真 MySQL)压测证明:朴素「读-改-写」在热点行上超卖 17%、库存被压成负数,而条件更新(CAS)方案精确履约 100 份、零超卖,吞吐约 7.6k 次/秒(P99 126ms)。

**English (for résumé)**:Eliminated inventory oversell under 1,000 concurrent requests by replacing read-modify-write logic with atomic conditional UPDATEs plus idempotency keys; benchmarked the naive approach overselling 17% with stock corrupted to −17, vs. 100/100 fulfilled at 7,600 reservations/sec (P99 126 ms).

## 项目里其它并发正确性证据(集成测试,不是压测但同样是并发验证)

- 一桌一活跃会话:16 线程并发扫码 → 恰好开出 1 个会话(`TableJoinIntegrationTest`)。
- 购物车原子性:20 线程并发加同一菜 → 数量恰好 20(`CartIntegrationTest`,Redis Lua)。
- 下单不超卖:12 线程抢 5 库存 → 恰好 5 成功(`OrderIntegrationTest`)。
- 幂等:重放同一 Idempotency-Key → 只下一单、只扣一次(`OrderIntegrationTest`)。
- 并发结账:10 线程并发结账 → 只出一张账单一条支付(`CheckoutIntegrationTest`)。
