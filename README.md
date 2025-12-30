# 🚀 High-Scale DB Shootout: PostgreSQL vs Cassandra

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Cassandra](https://img.shields.io/badge/Cassandra-1287B1?style=for-the-badge&logo=apache-cassandra&logoColor=white)](https://cassandra.apache.org/)
[![Vert.x](https://img.shields.io/badge/Vert.x-782b90?style=for-the-badge&logo=eclipse-vert.x&logoColor=white)](https://vertx.io/)

> **"Stop treating your database like a synchronous API."**

This project benchmarks the performance of **PostgreSQL** vs **Cassandra** when handing massive write loads (e.g., a WhatsApp-scale messaging system). It demonstrates the critical difference between **Synchronous**, **Batched**, and **Async Non-Blocking** I/O patterns.

---

## 📊 The "Thundering Herd" Results

We simulated 5,000+ concurrent users sending messages to see how each database architecture holds up.

| Approach | Database | Tech Stack | Throughput | Verdict |
|----------|----------|------------|------------|---------|
| **Sync** | Postgres | JDBC (Blocking) | ~270 req/s | ❌ Too Slow |
| **Sync** | Cassandra | Driver (Blocking) | ~433 req/s | ❌ Too Slow |
| **Buffered** | Postgres | Redis + JDBC Batch | ~1,193 req/s | ⚠️ Risk of Data Loss |
| **Async** | Postgres | **Vert.x Client** | **~2,720 req/s** | ✅ Good |
| **Async** | Cassandra | **Datastax Async** | **~32,798 req/s** | 🚀 **Winner** |

> **Why the huge difference?**
> *   **PostgreSQL (B-Tree)**: Random I/O. Every insert jumps around the disk to update indexes.
> *   **Cassandra (LSM Tree)**: Sequential I/O. Appends data to a commit log.

---

## 🏗️ Methodologies Tested

### 1. The "Naive" Approach (Synchronous)
*   **Files**: `NaiveInsert.java`, `NaiveCassandraInsert.java`
*   **Design**: One `INSERT` per message. Main thread waits for disk sync.
*   **Result**: CPU idle, massive latency.

### 2. The "Buffer" Trick (Redis + Batches)
*   **Files**: `OptimizedInsert.java`, `OptimizedInsertMetrics.java`
*   **Design**: Write to Redis → Accumulate 100 msgs → `COPY` to Postgres.
*   **Result**: 4x faster, but **unsafe**. Server crash = lost messages.

### 3. The "Async" Revolution (Non-Blocking)
*   **Files**: `OptimizedCassandraMetrics.java`, `OptimizedPostgresMetrics.java`
*   **Design**: Fire 1000s of requests, handle ACKs via callbacks. No thread blocking.
*   **Result**: Postgres 10x gain. Cassandra 75x gain.

---

## 🛠️ Quick Start

### Prerequisites
*   Java 17+
*   Maven
*   Docker (for running the DBs)

### 1. Start the Infrastructure
Spin up PostgreSQL and Cassandra using Docker:

```bash
# Start Postgres
docker run -d --name postgres -p 5432:5432 \
  -e POSTGRES_DB=whatsapp_db \
  -e POSTGRES_USER=ajay \
  -e POSTGRES_PASSWORD=password \
  postgres:15

# Start Cassandra
docker run -d --name cassandra -p 9042:9042 cassandra:latest
```
*Wait ~60 seconds for Cassandra to fully initialize.*

### 2. Build the Project
```bash
mvn clean install
```

### 3. Run the Benchmarks

#### 🟢 Run Cassandra Async Test (The Speedster)
```bash
mvn exec:java -Dexec.mainClass="org.example.OptimizedCassandraMetrics"
```
*   **Monitor Metrics**: `http://localhost:8080/metrics`
*   **Expected**: >30k/ops

#### 🔵 Run Postgres Async Test (The Workhorse)
```bash
mvn exec:java -Dexec.mainClass="org.example.OptimizedPostgresMetrics"
```
*   **Monitor Metrics**: `http://localhost:8081/metrics`
*   **Expected**: >2.5k/ops

---

## 📂 Project Structure

| File | Description |
|------|-------------|
| `OptimizedCassandraMetrics.java` | **Best Practice**: High-volume async writes for Cassandra. |
| `OptimizedPostgresMetrics.java` | **Best Practice**: Reactive Postgres client (Vert.x). |
| `OptimizedInsertMetrics.java` | **Alternative**: Redis buffering + Batch inserts. |
| `Naive*.java` | **Anti-Pattern**: Blocking synchronous code for comparison. |
| `GRAFANA_SETUP.md` | Instructions for visualizing metrics in Grafana. |

---

## 🧠 Key Takeaways

1.  **Async is King**: Unblocking threads resulted in massive gains for both DBs.
2.  **Architecture Matters**: Even with async code, Cassandra's LSM tree structure (Sequential writes) obliterates Postgres's B-Tree (Random writes) for ingestion tasks.
3.  **Don't Lie to Users**: Buffering in Redis gives speed but risks data loss. Async direct writes provide both **Speed** and **Durability** (client knows if write failed).

---

## 🤝 Contributing

Feel free to open a PR to add:
*   ScyllaDB benchmarks
*   Reactive Spring Boot implementations
*   Kafka-based buffering patterns
