package org.example;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

public class OptimizedInsertMetrics {

    // 1. Define Metrics
    static final Counter messagesProcessed = Counter.build()
            .name("whatsapp_messages_total")
            .help("Total messages persisted to DB.")
            .register();

    static final Histogram dbWriteLatency = Histogram.build()
            .name("whatsapp_db_write_latency_seconds")
            .help("Time taken to write a batch to Postgres.")
            .register();

    static class Message {
        public String id; public String content; public long timestamp;
        public Message(String id, String content, long timestamp) {
            this.id = id; this.content = content; this.timestamp = timestamp;
        }
        public Message() {}
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        try {
            // 2. Start Metrics Server (Prometheus scrapes this)
            HTTPServer server = new HTTPServer(8080);
            System.out.println("📊 Metrics Server started on Port 8080");

            ObjectMapper mapper = new ObjectMapper();

            // FILL REDIS (Simulation)
            System.out.println("🚀 Filling Redis Buffer...");
            try (Jedis jedis = new Jedis("localhost", 6379)) {
                jedis.del("msg_queue");
                Pipeline p = jedis.pipelined();
                for (int i = 0; i < 20000; i++) { // Increased to 20k to give you time to look at Grafana
                    Message msg = new Message(UUID.randomUUID().toString(), "Payload", System.currentTimeMillis());
                    p.rpush("msg_queue", mapper.writeValueAsString(msg));
                }
                p.sync();
            }

            // WORKER LOOP
            String url = "jdbc:postgresql://localhost:5432/whatsapp_db";
            try (Connection conn = DriverManager.getConnection(url, "ajay", "password")) {
                conn.setAutoCommit(false);

                try (Jedis jedis = new Jedis("localhost", 6379);
                     PreparedStatement pstmt = conn.prepareStatement("INSERT INTO messages (id, content, timestamp) VALUES (?, ?, ?)")) {

                    System.out.println("⚡ Worker started. Go check Grafana!");

                    while (true) {
                        List<String> batch = jedis.lpop("msg_queue", 100);
                        if (batch == null || batch.isEmpty()) {
                            // Sleep briefly to keep app alive so metrics don't die
                            Thread.sleep(100);
                            continue;
                        }

                        // Timer Start
                        Histogram.Timer requestTimer = dbWriteLatency.startTimer();

                        for (String json : batch) {
                            Message msg = mapper.readValue(json, Message.class);
                            pstmt.setObject(1, UUID.fromString(msg.id));
                            pstmt.setString(2, msg.content);
                            pstmt.setLong(3, msg.timestamp);
                            pstmt.addBatch();
                        }

                        pstmt.executeBatch();
                        conn.commit();

                        // Timer Stop & Count Update
                        requestTimer.observeDuration();
                        messagesProcessed.inc(batch.size());

                        // Artificial delay just so you have time to switch tabs to Grafana
                        Thread.sleep(50);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}