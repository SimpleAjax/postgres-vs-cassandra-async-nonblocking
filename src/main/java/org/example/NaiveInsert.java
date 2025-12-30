package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.TimeZone;
import java.util.UUID;

public class NaiveInsert {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        String url = "jdbc:postgresql://localhost:5432/whatsapp_db";
        String user = "ajay";
        String password = "password";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            // Setup Table
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS messages (id UUID PRIMARY KEY, content TEXT, timestamp BIGINT)");
                stmt.execute("TRUNCATE TABLE messages"); // Clear previous runs
            }

            conn.setAutoCommit(false); // We will commit manually to simulate the bottleneck
            String sql = "INSERT INTO messages (id, content, timestamp) VALUES (?, ?, ?)";

            System.out.println("🚀 Starting Naive Java Insertion (10,000 rows)...");
            long startTime = System.currentTimeMillis();

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < 10000; i++) {
                    pstmt.setObject(1, UUID.randomUUID());
                    pstmt.setString(2, "Hello World Payload");
                    pstmt.setLong(3, System.currentTimeMillis());

                    pstmt.executeUpdate();

                    // THE KILLER: Committing every single row triggers a Disk Sync (fsync)
                    conn.commit();
                }
            }

            long endTime = System.currentTimeMillis();
            double seconds = (endTime - startTime) / 1000.0;
            System.out.printf("❌ Naive Result: 10,000 messages took %.2f seconds.\n", seconds);
            System.out.printf("📉 Throughput: %.2f msg/sec\n", 10000 / seconds);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}