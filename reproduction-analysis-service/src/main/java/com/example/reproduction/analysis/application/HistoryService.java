package com.example.reproduction.analysis.application;

import com.example.reproduction.messaging.AnalysisEvent;
import com.example.reproduction.platform.redis.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Historical analysis: for every field, how often it was present in analysed inputs ("seen") and how often it
 * survived into a minimal reproduction ("kept"). keepLikelihood = kept / seen is used as a priority hint:
 * fields that rarely matter are put in the first ddmin partitions.
 * Redis counters (atomic INCR) make this safe under concurrent consumers; a dedup key makes it idempotent per job.
 */
@Service
public class HistoryService {
    private final StringRedisTemplate redis;

    public HistoryService(StringRedisTemplate redis) { this.redis = redis; }

    /** @return false if this job's event was already applied (duplicate delivery). */
    public boolean record(AnalysisEvent e) {
        Boolean first = redis.opsForValue().setIfAbsent(RedisKeys.dedup("analysis", e.jobId()), "1", RedisKeys.DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) return false;
        Set<String> kept = new HashSet<>();
        e.minimalCandidates().forEach(kept::addAll);
        for (String f : e.originalFields()) {
            redis.opsForValue().increment(seenKey(f));
            if (kept.contains(f)) redis.opsForValue().increment(keptKey(f));
        }
        return true;
    }

    public Map<String, Double> keepHints(Collection<String> paths) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String p : paths) {
            Double h = keepLikelihood(p);
            if (h != null) out.put(p, h);
        }
        return out;
    }

    public Double keepLikelihood(String path) {
        long seen = num(redis.opsForValue().get(seenKey(path)));
        if (seen == 0) return null;
        return num(redis.opsForValue().get(keptKey(path))) / (double) seen;
    }

    public long seen(String path) { return num(redis.opsForValue().get(seenKey(path))); }
    public long kept(String path) { return num(redis.opsForValue().get(keptKey(path))); }

    private static long num(String s) { return s == null ? 0 : Long.parseLong(s); }
    private static String seenKey(String p) { return "analysis:seen:" + p; }
    private static String keptKey(String p) { return "analysis:kept:" + p; }
}
