package org.example;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class OptimizedCassandraMetrics {

    // Manual tracking for latency calculation
    private static final AtomicLong totalLatencyNanos = new AtomicLong(0);

    // --- METRICS DEFINITIONS ---
    // 1. Throughput: How many writes we are doing
    static final Counter writesTotal = Counter.build()
            .name("cassandra_writes_total")
            .help("Total writes to Cassandra.")
            .register();

    // 2. Latency: How long each async write takes (from send to callback)
    static final Histogram writeLatency = Histogram.build()
            .name("cassandra_write_latency_seconds")
            .help("Time taken for Cassandra to ack the write.")
            .buckets(0.001, 0.002, 0.004, 0.010, 0.025, 0.050, 0.100) // Buckets: 0.5ms, 1ms, 5ms...
            .register();

    // 3. Concurrency: How many requests are currently in the network pipe
    static final Gauge inflightRequests = Gauge.build()
            .name("cassandra_inflight_requests")
            .help("Number of async requests currently waiting for a response.")
            .register();

    private static final int MAX_IN_FLIGHT = 1024;

    public static void main(String[] args) {
        try {
            // Start the Metrics Server (Prometheus scrapes this)
            // Ensure you stopped the previous Postgres app so Port 8080 is free!
            HTTPServer metricsServer = new HTTPServer(8080);
            System.out.println("📊 Metrics Server listening on http://localhost:8080/metrics");

            try (CqlSession session = CqlSession.builder()
                    .addContactPoint(new InetSocketAddress("localhost", 9042))
                    .withLocalDatacenter("datacenter1")
                    .build()) {

                session.execute(
                        "CREATE KEYSPACE IF NOT EXISTS whatsapp WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
                session.execute(
                        "CREATE TABLE IF NOT EXISTS whatsapp.messages (id uuid PRIMARY KEY, content text, timestamp bigint)");

                PreparedStatement ps = session
                        .prepare("INSERT INTO whatsapp.messages (id, content, timestamp) VALUES (?, ?, ?)");

                // Increase loop to 100k so you have time to watch the graphs
                int totalMessages = 1000000;
                CountDownLatch allDone = new CountDownLatch(totalMessages);
                Semaphore inflightLimiter = new Semaphore(MAX_IN_FLIGHT);

                System.out.println("🚀 Starting Async Load Test...");
                System.out.println("━".repeat(80));

                // Track total processing time
                long startTime = System.nanoTime();
                final int[] processedCount = { 0 };

                for (int i = 0; i < totalMessages; i++) {
                    inflightLimiter.acquire();

                    // Increment Gauge: We are sending a request out
                    inflightRequests.inc();

                    Histogram.Timer timer = writeLatency.startTimer();

                    CompletionStage<AsyncResultSet> future = session.executeAsync(ps.bind(
                            UUID.randomUUID(), "Payload", System.currentTimeMillis()));

                    future.whenComplete((result, error) -> {
                        // Stop Timer & Decrement Gauge
                        double durationSeconds = timer.observeDuration();
                        long durationNanos = (long) (durationSeconds * 1_000_000_000);
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
                                        processedCount[0], totalMessages, elapsedMs, avgLatencyMs, requestsPerSecond);
                            }
                        }

                        if (error != null)
                            error.printStackTrace();
                    });

                    // Tiny sleep to prevent the test from finishing in 2 seconds
                    // We want to sustain load to look at the dashboard
                    // if (i % 1000 == 0) Thread.sleep(50);
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

                // Keep server alive so you can still read the final metrics
                System.out.println("🔄 Keeping metrics server alive for 60 seconds...");
                Thread.sleep(60000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}