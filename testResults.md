NaiveInsert got following results:

```
🚀 Starting Naive Java Insertion (10,000 rows)...
❌ Naive Result: 10,000 messages took 36.92 seconds.
📉 Throughput: 270.88 msg/sec
```

OptimizedInsert got following results:

```
🚀 Starting Optimized Ingestion (Redis Buffer)...
✅ Redis Buffer filled. Starting Async Worker...
⚡ Optimized Result: Total time 8.38 seconds.
📈 Throughput: 1193.60 msg/sec
```

NaiveCassndraInsert got following results

```
🚀 Starting Naive Cassandra Insertion (10,000 rows)...
------------------------------------------------
❌ Naive Cassandra Result: 10,000 messages took 23.06 seconds.
📉 Throughput: 433.75 msg/sec
------------------------------------------------
```

OptimizedPostgresMetrics got following results:

```
📊 Total Messages Processed: 100,000
⏱️  Total Processing Time: 36,753 ms (36.75 seconds)
⚡ Average Processing Time per Message: 0.3675 ms
🚀 Average Throughput: 2720.87 requests/second
📉 Average Write Latency: 374.1167 ms
📊 Total Writes Completed: 100,000

```


OptimizedCassandraMetrics got following results:
```
📊 Total Messages Processed: 1,000,000
⏱️  Total Processing Time: 30,489 ms (30.49 seconds)
⚡ Average Processing Time per Message: 0.0305 ms
🚀 Average Throughput: 32798.71 requests/second
📉 Average Write Latency: 30.1257 ms
📊 Total Writes Completed: 1,000,000
```

