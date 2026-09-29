package com.example.reproduction.algorithm.search;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded evaluation pool: fixed threads + bounded queue + caller-runs = back-pressure instead of unbounded growth. */
public final class ParallelExecutors {
    private ParallelExecutors() {}

    public static ThreadPoolExecutor bounded(int threads, String namePrefix) {
        AtomicInteger n = new AtomicInteger();
        ThreadFactory tf = r -> { Thread t = new Thread(r, namePrefix + "-" + n.incrementAndGet()); t.setDaemon(true); return t; };
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(4, threads * 4)), tf, new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
