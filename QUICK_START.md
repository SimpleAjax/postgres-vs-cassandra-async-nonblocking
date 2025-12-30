# Quick Start Guide: Async PostgreSQL vs Cassandra

## 🎯 What You Have Now

Three different approaches to test write performance:

1. **OptimizedCassandraMetrics.java** - Async Cassandra (LSM-tree database)
2. **OptimizedPostgresMetrics.java** - Async PostgreSQL (using Vert.x reactive client)
3. **OptimizedInsertMetrics.java** - Batched PostgreSQL with Redis buffer

## 🚀 Quick Start

### Step 1: Update Dependencies
```bash
mvn clean install
```

### Step 2: Start Databases

**PostgreSQL:**
```bash
docker run -d --name postgres -p 5432:5432 \
  -e POSTGRES_DB=whatsapp_db \
  -e POSTGRES_USER=ajay \
  -e POSTGRES_PASSWORD=password \
  postgres:15
```

**Cassandra:**
```bash
docker run -d --name cassandra -p 9042:9042 cassandra:latest
```

Wait ~60 seconds for Cassandra to be fully ready.

### Step 3: Run Tests

**Test 1: Async Cassandra**
```bash
mvn exec:java -Dexec.mainClass="org.example.OptimizedCassandraMetrics"
```
- Metrics: http://localhost:8080/metrics
- Expected: ~50,000-100,000+ req/s

**Test 2: Async PostgreSQL**
```bash
mvn exec:java -Dexec.mainClass="org.example.OptimizedPostgresMetrics"
```
- Metrics: http://localhost:8081/metrics
- Expected: ~10,000-30,000 req/s

## 📊 What Gets Logged

Both tests log:
- ✅ Progress every 10,000 messages
- ✅ Average latency (ms)
- ✅ Throughput (requests/second)
- ✅ Final summary with all metrics

## 🎓 Key Differences

| Feature | Cassandra | PostgreSQL |
|---------|-----------|------------|
| Architecture | LSM-tree | B-tree |
| Write Speed | Very Fast | Fast |
| Consistency | Tunable | ACID |
| Best For | Writes, Scale | Queries, Transactions |
| Expected Latency | 1-5ms | 5-20ms |

## 💡 Tips

1. **Run one at a time** - They use different ports (8080 vs 8081)
2. **Watch the console** - Real-time metrics appear during the test
3. **Compare results** - Note the throughput differences
4. **Check metrics** - Visit the /metrics endpoints

## 🔧 Troubleshooting

**Port already in use:**
- Cassandra test uses port 8080
- PostgreSQL test uses port 8081
- Make sure previous tests are stopped

**Connection refused:**
- Wait 60 seconds after starting Cassandra
- Check PostgreSQL is running: `docker ps`

**Out of memory:**
- Reduce `totalMessages` from 1,000,000 to 100,000
- Adjust `MAX_IN_FLIGHT` semaphore

## 📈 Understanding Results

**Cassandra will be faster because:**
- LSM-tree architecture = sequential writes
- No immediate disk seeks
- Optimized for write throughput

**PostgreSQL has higher latency because:**
- ACID transactions require WAL writes
- B-tree indexes need updating
- Stronger consistency guarantees

Both are async though, so both should handle high concurrency well!
