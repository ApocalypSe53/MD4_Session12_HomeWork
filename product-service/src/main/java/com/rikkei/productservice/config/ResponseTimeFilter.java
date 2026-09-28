package com.rikkei.productservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/** Gắn header X-Response-Time (ms) để so sánh thời gian đọc DB và đọc Redis. */
@Component
public class ResponseTimeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ResponseTimeFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Buffer body để header vẫn set được sau khi controller chạy xong.
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, wrapper);
        } finally {
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            wrapper.setHeader("X-Response-Time", String.format("%.2fms", ms));
            log.info("{} {} -> {} in {}ms", request.getMethod(), request.getRequestURI(),
                    wrapper.getStatus(), String.format("%.2f", ms));
            wrapper.copyBodyToResponse();
        }
    }
}
