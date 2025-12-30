package org.example;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class OptimizedPostgresMetrics {

    // Manual tracking for latency calculation
    private static final AtomicLong totalLatencyNanos = new AtomicLong(0);

    // --- METRICS DEFINITIONS ---
    // 1. Throughput: How many writes we are doing
    static final Counter writesTotal = Counter.build()
            .name("postgres_writes_total")
            .help("Total writes to PostgreSQL.")
            .register();

    // 2. Latency: How long each async write takes (from send to callback)
    static final Histogram writeLatency = Histogram.build()
            .name("postgres_write_latency_seconds")
            .help("Time taken for PostgreSQL to ack the write.")
            .buckets(0.001, 0.002, 0.004, 0.010, 0.025, 0.050, 0.100) // Buckets: 1ms, 2ms, 4ms, 10ms...
            .register();

    // 3. Concurrency: How many requests are currently in the network pipe
    static final Gauge inflightRequests = Gauge.build()
            .name("postgres_inflight_requests")
            .help("Number of async requests currently waiting for a response.")
            .register();

    private static final int MAX_IN_FLIGHT = 1024;

    public static void main(String[] args) {
        try {
            // Start the Metrics Server (Prometheus scrapes this)
            HTTPServer metricsServer = new HTTPServer(8081); // Using 8081 to avoid conflict with Cassandra
            System.out.println("📊 Metrics Server listening on http://localhost:8081/metrics");

            Vertx vertx = Vertx.vertx();

            // Configure PostgreSQL connection
            PgConnectOptions connectOptions = new PgConnectOptions()
                    .setPort(5432)
                    .setHost("localhost")
                    .setDatabase("whatsapp_db")
                    .setUser("ajay")
                    .setPassword("password");

            // Pool options - this controls connection pooling
            PoolOptions poolOptions = new PoolOptions()
                    .setMaxSize(50); // Adjust based on your system

            PgPool client = PgPool.pool(vertx, connectOptions, poolOptions);

            // Create table if not exists (synchronous, just for setup)
            System.out.println("🔧 Setting up database...");
            CountDownLatch setupLatch = new CountDownLatch(1);

            client.query("CREATE TABLE IF NOT EXISTS messages (id uuid PRIMARY KEY, content text, timestamp bigint)")
                    .execute()
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            System.out.println("✅ Database setup complete");
                        } else {
                            System.err.println("❌ Failed to create table: " + ar.cause().getMessage());
                        }
                        setupLatch.countDown();
                    });

            setupLatch.await();

            int totalMessages = 100000;
            CountDownLatch allDone = new CountDownLatch(totalMessages);
            Semaphore inflightLimiter = new Semaphore(MAX_IN_FLIGHT);

            System.out.println("🚀 Starting Async Load Test...");
            System.out.println("━".repeat(80));

            // Track total processing time
            long startTime = System.nanoTime();
            final int[] processedCount = { 0 };

            String insertQuery = "INSERT INTO messages (id, content, timestamp) VALUES ($1, $2, $3)";

            for (int i = 0; i < totalMessages; i++) {
                inflightLimiter.acquire();

                // Increment Gauge: We are sending a request out
                inflightRequests.inc();

                long requestStartTime = System.nanoTime();

                UUID messageId = UUID.randomUUID();
                Tuple params = Tuple.of(messageId, "Payload", System.currentTimeMillis());

                client.preparedQuery(insertQuery)
                        .execute(params)
                        .onComplete(ar -> {
                            // Calculate duration
                            long requestEndTime = System.nanoTime();
                            long durationNanos = requestEndTime - requestStartTime;
                            double durationSeconds = durationNanos / 1_000_000_000.0;

                            // Update metrics
                            writeLatency.observe(durationSeconds);
                            totalLatencyNanos.addAndGet(durationNanos);
                            inflightRequests.dec();
                            writesTotal.inc();

                            inflightLimiter.release();
                            allDone.countDown();

                            synchronized (processedCount) {
                                processedCount[0]++;

                                // Log progress every 10,000 messages
                                if (processedCount[0] % 10000 == 0) {
                                    long currentTime = System.nanoTime();
                                    long elapsedMs = (currentTime - startTime) / 1_000_000;
                                    double avgLatencyMs = (totalLatencyNanos.get() / (double) processedCount[0])
                                            / 1_000_000;
                                    double requestsPerSecond = (processedCount[0] * 1000.0) / elapsedMs;

                                    System.out.printf(
                                            "📈 Progress: %,d/%,d messages | Elapsed: %,d ms | Avg Latency: %.2f ms | Throughput: %.2f req/s%n",
                                            processedCount[0], totalMessages, elapsedMs, avgLatencyMs,
                                            requestsPerSecond);
                                }
                            }

                            if (ar.failed()) {
                                System.err.println("❌ Write failed: " + ar.cause().getMessage());
                            }
                        });
            }

            allDone.await();

            // Calculate final metrics
            long endTime = System.nanoTime();
            long totalTimeMs = (endTime - startTime) / 1_000_000;
            double totalTimeSec = totalTimeMs / 1000.0;
            double avgProcessingTimeMs = (double) totalTimeMs / totalMessages;
            double avgRequestsPerSecond = totalMessages / totalTimeSec;

            // Calculate average latency from tracked data
            double avgLatencyMs = (totalLatencyNanos.get() / (double) totalMessages) / 1_000_000;

            System.out.println("━".repeat(80));
            System.out.println("✅ Test Finished - FINAL METRICS");
            System.out.println("━".repeat(80));
            System.out.printf("📊 Total Messages Processed: %,d%n", totalMessages);
            System.out.printf("⏱️  Total Processing Time: %,d ms (%.2f seconds)%n", totalTimeMs, totalTimeSec);
            System.out.printf("⚡ Average Processing Time per Message: %.4f ms%n", avgProcessingTimeMs);
            System.out.printf("🚀 Average Throughput: %.2f requests/second%n", avgRequestsPerSecond);
            System.out.printf("📉 Average Write Latency: %.4f ms%n", avgLatencyMs);
            System.out.printf("📊 Total Writes Completed: %,d%n", (long) writesTotal.get());
            System.out.println("━".repeat(80));

            // Graceful shutdown
            System.out.println("🔄 Keeping metrics server alive for 60 seconds...");
            Thread.sleep(60000);

            client.close();
            vertx.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
