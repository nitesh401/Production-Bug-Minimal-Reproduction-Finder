package com.example.reproduction.platform.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Accepts or creates X-Correlation-Id, exposes it to logs (MDC) and echoes it in the response. */
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String id = req.getHeader(HEADER);
        if (id == null || id.isBlank() || id.length() > 100) id = UUID.randomUUID().toString();
        res.setHeader(HEADER, id);
        try (Correlation c = Correlation.of(id, null, null, null)) {
            chain.doFilter(req, res);
        }
    }
}
