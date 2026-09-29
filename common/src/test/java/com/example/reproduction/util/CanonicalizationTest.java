package com.example.reproduction.util;

import com.example.reproduction.domain.Candidate;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalizationTest {

    @Test
    void hashIsIndependentOfInsertionOrderAndNumberFormat() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("amount", 15000); a.put("currency", "INR");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("currency", "INR"); b.put("amount", 15000.0);
        assertEquals(Candidate.of(a).hash(), Candidate.of(b).hash());
        assertEquals(Candidate.of(a).canonical(), Candidate.of(b).canonical());
    }

    @Test
    void differentValuesProduceDifferentHashes() {
        assertNotEquals(Candidate.of(Map.of("a", 1)).hash(), Candidate.of(Map.of("a", 2)).hash());
        assertNotEquals(Candidate.of(Map.of("a", 1)).hash(), Candidate.of(Map.of("b", 1)).hash());
        assertEquals(64, Candidate.of(Map.of("a", 1)).hash().length());
    }

    @Test
    void canonicalJsonSortsNestedKeysAndEscapes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("z", 1); m.put("a", Map.of("y", "q\"x", "b", true));
        assertEquals("{\"a\":{\"b\":true,\"y\":\"q\\\"x\"},\"z\":1}", CanonicalJson.write(m));
        assertEquals("15000", CanonicalJson.write(1.5E4));
        assertEquals("0.5", CanonicalJson.write(0.50));
    }

    @Test
    void flattenAndUnflattenRoundTrip() {
        Map<String, Object> nested = Map.of("a", Map.of("b", Map.of("c", 1), "d", 2), "e", 3);
        Map<String, Object> flat = InputFlattener.flatten(nested);
        assertEquals(3, flat.size());
        assertEquals(1, flat.get("a.b.c"));
        assertEquals(nested, InputFlattener.unflatten(flat));
        assertTrue(InputFlattener.has(nested, "a.b.c"));
        assertFalse(InputFlattener.has(nested, "a.x"));
    }
}
