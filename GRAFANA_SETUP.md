# 📊 Simple Grafana Dashboard Guide

## 1. Prometheus Configuration (`prometheus.yml`)

Add these jobs to your `scrape_configs` to collect data from your running apps:

```yaml
scrape_configs:
  # Cassandra Async App (Port 8080)
  - job_name: 'cassandra_app'
    scrape_interval: 1s
    static_configs:
      - targets: ['host.docker.internal:8080'] # Use 'localhost:8080' if running locally without Docker

  # Postgres Async App (Port 8081)
  - job_name: 'postgres_app'
    scrape_interval: 1s
    static_configs:
      - targets: ['host.docker.internal:8081']
```

---

## 2. Grafana Dashboard Panels

Create a **New Dashboard** and add these 4 panels.

### 🚀 Panel 1: Throughput (Requests/Sec)
**Visualization:** Time Series
**Title:** Write Throughput
**Unit:** Requests/sec

**Queries:**
*   **Cassandra:**
    ```promql
    rate(cassandra_writes_total[5s])
    ```
    *Legend: Cassandra Async*

*   **Postgres:**
    ```promql
    rate(postgres_writes_total[5s])
    ```
    *Legend: Postgres Async*

---

### 📉 Panel 2: Write Latency (P99 & Average)
**Visualization:** Time Series
**Title:** Write Latency (ms)
**Unit:** Milliseconds (ms)

**Queries:**

*   **Cassandra (P99):**
    ```promql
    histogram_quantile(0.99, rate(cassandra_write_latency_seconds_bucket[5s])) * 1000
    ```
    *Legend: Cassandra P99*

*   **Cassandra (Avg):**
    ```promql
    rate(cassandra_write_latency_seconds_sum[5s]) / rate(cassandra_write_latency_seconds_count[5s]) * 1000
    ```
    *Legend: Cassandra Avg*

*   **Postgres (P99):**
    ```promql
    histogram_quantile(0.99, rate(postgres_write_latency_seconds_bucket[5s])) * 1000
    ```
    *Legend: Postgres P99*

*   **Postgres (Avg):**
    ```promql
    rate(postgres_write_latency_seconds_sum[5s]) / rate(postgres_write_latency_seconds_count[5s]) * 1000
    ```
    *Legend: Postgres Avg*

---

### 📦 Panel 3: In-Flight Requests (Concurrency)
**Visualization:** Gauge or Time Series
**Title:** In-Flight Requests
**Description:** How many requests are currently waiting for a DB response.

**Queries:**
*   **Cassandra:**
    ```promql
    cassandra_inflight_requests
    ```
*   **Postgres:**
    ```promql
    postgres_inflight_requests
    ```

---

### 🔢 Panel 4: Total Writes (Counter)
**Visualization:** Stat
**Title:** Total Messages Written

**Queries:**
*   **Cassandra:**
    ```promql
    cassandra_writes_total
    ```
*   **Postgres:**
    ```promql
    postgres_writes_total
    ```

---

## 💡 Quick Tips
1.  **Resolution:** Set the dashboard time range to **"Last 5 minutes"** or **"Last 15 minutes"** and refresh rate to **"1s"** or **"5s"** to see live updates.
2.  **Comparison:** Run both apps simultaneously (in different terminals) to see the lines race against each other on the same graph!
