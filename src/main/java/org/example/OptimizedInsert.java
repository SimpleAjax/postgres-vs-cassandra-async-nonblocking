package org.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

public class OptimizedInsert {

    // Simple POJO for JSON serialization
    static class Message {
        public String id;
        public String content;
        public long timestamp;

        public Message(String id, String content, long timestamp) {
            this.id = id; this.content = content; this.timestamp = timestamp;
        }
        public Message() {} // needed for Jackson
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        ObjectMapper mapper = new ObjectMapper();

        // 1. Ingestion Phase: Client -> Redis (In-Memory)
        // In real life, this happens in your API Gateway
        System.out.println("🚀 Starting Optimized Ingestion (Redis Buffer)...");
        long startTotal = System.currentTimeMillis();

        try (Jedis jedis = new Jedis("localhost", 6379)) {
            jedis.del("msg_queue"); // clear old queue
            Pipeline p = jedis.pipelined();

            for (int i = 0; i < 100000; i++) {
                Message msg = new Message(UUID.randomUUID().toString(), "Hello World Payload", System.currentTimeMillis());
                p.rpush("msg_queue", mapper.writeValueAsString(msg));
            }
            p.sync(); // Fire all at once
        } catch (Exception e) { e.printStackTrace(); }

        System.out.println("✅ Redis Buffer filled. Starting Async Worker...");

        // 2. Worker Phase: Redis -> Postgres (Batch Insert)
        // In real life, this runs as a background microservice
        String url = "jdbc:postgresql://localhost:5432/whatsapp_db";
        try (Connection conn = DriverManager.getConnection(url, "ajay", "password")) {
            conn.setAutoCommit(false);

            try (Jedis jedis = new Jedis("localhost", 6379);
                 PreparedStatement pstmt = conn.prepareStatement("INSERT INTO messages (id, content, timestamp) VALUES (?, ?, ?)")) {

                while (true) {
                    // Pull batch of 100 from Redis
                    List<String> batch = jedis.lpop("msg_queue", 100);
                    if (batch == null || batch.isEmpty()) break;

                    for (String json : batch) {
                        Message msg = mapper.readValue(json, Message.class);
                        pstmt.setObject(1, UUID.fromString(msg.id));
                        pstmt.setString(2, msg.content);
                        pstmt.setLong(3, msg.timestamp);
                        pstmt.addBatch(); // Add to JDBC Batch memory
                    }

                    pstmt.executeBatch(); // Send 100 rows in 1 Network Packet
                    conn.commit();        // Sync to disk only once per 100 rows
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        long endTotal = System.currentTimeMillis();
        double seconds = (endTotal - startTotal) / 1000.0;
        System.out.printf("⚡ Optimized Result: Total time %.2f seconds.\n", seconds);
        System.out.printf("📈 Throughput: %.2f msg/sec\n", 10000 / seconds);
    }
}
