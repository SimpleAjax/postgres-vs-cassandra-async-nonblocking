package org.example;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class OptimizedCassandraInsert {

    // Safety Valve: Limit concurrent in-flight requests to prevent OOM errors on the client
    // or overwhelming the Docker container. 1024 is a healthy "Staff" default.
    private static final int MAX_IN_FLIGHT = 1024;

    public static void main(String[] args) {

        try (CqlSession session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress("localhost", 9042))
                .withLocalDatacenter("datacenter1")
                .build()) {

            System.out.println("✅ Connected to Cassandra. Preparing...");

            // 1. Setup Keyspace & Table (Idempotent)
            session.execute("CREATE KEYSPACE IF NOT EXISTS whatsapp WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
            session.execute("CREATE TABLE IF NOT EXISTS whatsapp.messages (id uuid PRIMARY KEY, content text, timestamp bigint)");

            PreparedStatement ps = session.prepare("INSERT INTO whatsapp.messages (id, content, timestamp) VALUES (?, ?, ?)");

            int totalMessages = 20000; // Increased to 20k to see the speed difference
            CountDownLatch allDone = new CountDownLatch(totalMessages);
            Semaphore inflightLimiter = new Semaphore(MAX_IN_FLIGHT);
            AtomicInteger errorCount = new AtomicInteger(0);

            System.out.println("🚀 Starting Async Cassandra Insertion (" + totalMessages + " rows)...");
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < totalMessages; i++) {
                // Block if we have too many requests pending (Backpressure)
                inflightLimiter.acquire();

                // 2. Fire Async (Non-Blocking)
                CompletionStage<AsyncResultSet> future = session.executeAsync(ps.bind(
                        UUID.randomUUID(),
                        "Async High Throughput Payload",
                        System.currentTimeMillis()
                ));

                // 3. Handle Completion (Callback)
                future.whenComplete((result, error) -> {
                    inflightLimiter.release(); // Allow new request
                    allDone.countDown();       // Decrement pending count

                    if (error != null) {
                        errorCount.incrementAndGet();
                        // In real life, log this error or retry
                    }
                });
            }

            // 4. Wait for the last async callback to finish
            allDone.await();

            long endTime = System.currentTimeMillis();
            double seconds = (endTime - startTime) / 1000.0;

            System.out.println("------------------------------------------------");
            System.out.printf("⚡ Optimized Result: %d messages took %.2f seconds.\n", totalMessages, seconds);
            System.out.printf("📈 Throughput: %.2f msg/sec\n", totalMessages / seconds);
            System.out.println("Errors: " + errorCount.get());
            System.out.println("------------------------------------------------");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
