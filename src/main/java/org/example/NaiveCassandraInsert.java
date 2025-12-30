package org.example;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import java.net.InetSocketAddress;
import java.util.UUID;

public class NaiveCassandraInsert {
    public static void main(String[] args) {
        // 1. Connect to Cassandra (Docker)
        // Default DC is 'datacenter1' in the official image
        try (CqlSession session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress("localhost", 9042))
                .withLocalDatacenter("datacenter1")
                .build()) {

            System.out.println("✅ Connected to Cassandra. Setting up Schema...");

            // 2. Setup Keyspace (Like a Database in SQL)
            // Replication Factor 1 because we only have 1 node in Docker
            session.execute(SimpleStatement.builder(
                            "CREATE KEYSPACE IF NOT EXISTS whatsapp " +
                                    "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}")
                    .build());

            // 3. Setup Table
            // Partition Key: id (simulating random distribution)
            session.execute(SimpleStatement.builder(
                            "CREATE TABLE IF NOT EXISTS whatsapp.messages (" +
                                    "id uuid PRIMARY KEY, content text, timestamp bigint)")
                    .build());

            // 4. Prepare the Statement (Compiles query once for performance)
            PreparedStatement ps = session.prepare(
                    "INSERT INTO whatsapp.messages (id, content, timestamp) VALUES (?, ?, ?)");

            System.out.println("🚀 Starting Naive Cassandra Insertion (10,000 rows)...");
            long startTime = System.currentTimeMillis();

            // 5. The "Naive" Loop
            for (int i = 0; i < 10000; i++) {
                // EXECUTE BLOCKING:
                // The driver waits for the node to acknowledge the write before moving to the next line.
                // This mimics the synchronous behavior of a standard SQL INSERT.
                session.execute(ps.bind(
                        UUID.randomUUID(),
                        "Hello World Payload",
                        System.currentTimeMillis()
                ));
            }

            long endTime = System.currentTimeMillis();
            double seconds = (endTime - startTime) / 1000.0;

            System.out.println("------------------------------------------------");
            System.out.printf("❌ Naive Cassandra Result: 10,000 messages took %.2f seconds.\n", seconds);
            System.out.printf("📉 Throughput: %.2f msg/sec\n", 10000 / seconds);
            System.out.println("------------------------------------------------");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
