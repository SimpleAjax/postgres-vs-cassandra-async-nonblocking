# 📄 LinkedIn Post Draft: Why Your Chat App Will Die Without Async Writes

**Stop treating your database like a synchronous API.**

I recently built a simulation of a WhatsApp-scale messaging system to test how different databases handle the "Thundering Herd" problem—5,000+ users sending messages simultaneously.

The results weren't just surprising; they were a wake-up call for anyone designing high-scale systems.

Here is the breakdown of my journey from 270 req/s to 32,000+ req/s. 🚀

---

### 1️⃣ Phase 1: The "Naive" Approach (Synchronous)
I started how we all start: A simple `INSERT INTO messages` loop in Java. I tried this with both Postgres and Cassandra.

*   ❌ **Postgres (Sync):** 270 msg/sec
*   ❌ **Cassandra (Sync):** 433 msg/sec

**The Problem?** Blocking I/O.
Every single message forced the main thread to wait for a network roundtrip and a disk sync. It felt like trying to fill a swimming pool with a teaspoon.

---

### 2️⃣ Phase 2: The "Buffer" Trick (Redis + Batches)
Next, I introduced a Redis Write-Back buffer. We write to Redis instantly, accumulate 100 messages, and then `COPY` them to Postgres in one go.

*   ✅ **Redis + Postgres Batching:** 1,193 msg/sec (~4x boost)

**The Catch?** ⚠️ **Durability.**
If the server crashes while messages are in the Redis buffer, **that data is gone forever.** You told the user "Sent ✅" but the database never saved it. For a "Typing..." indicator, this is fine. For a financial transaction or a critical message? It's a disaster waiting to happen.

---

### 3️⃣ Phase 3: The "Async" Revolution
Finally, I stripped away the buffers and went full Non-Blocking I/O using **Vert.x (for Postgres)** and the **Datastax Async Driver (for Cassandra)**.

Instead of waiting for the database to say "OK", we fire thousands of requests and handle the acknowledgments via callbacks.

**The Results:**
*   🚀 **Postgres (Async):** 2,720 msg/sec
*   🤯 **Cassandra (Async):** 32,798 msg/sec

---

### 💡 The Conclusion

**1. Async is King:** Both databases saw massive gains just by unblocking the thread. Postgres jumped 10x (270 → 2720).

**2. Architecture Matters (LSM vs B-Tree):**
Even with the exact same Async code, **Cassandra was ~12x faster than Postgres.**
Why?
*   **Postgres (B-Tree):** Every insert requires jumping around the disk to update indexes (Random I/O).
*   **Cassandra (LSM Tree):** Appends data sequentially to a commit log (Sequential I/O).

**3. Don't Lie to your Users:**
By using **Async Direct** writes instead of a **Redis Buffer**, we gain safety. If the write fails, the callback fails, and the User's phone knows to **Retry**. No silent data loss.

**The Lesson?**
If you are building a read-heavy app (dashboards, e-commerce), stick with Postgres. But if you need to ingest millions of events or messages? You need an LSM-tree database and Non-Blocking I/O.

#SystemDesign #Scalability #Java #Postgres #Cassandra #DistributedSystems #BackendEngineering
