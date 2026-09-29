package com.example.reproduction.domain;

import com.example.reproduction.util.CanonicalJson;

/** One leaf of the (flattened) production input, addressed by dotted path. */
public record InputField(String path, Object value) {
    public int payloadSize() { return path.length() + CanonicalJson.write(value).length(); }
}
