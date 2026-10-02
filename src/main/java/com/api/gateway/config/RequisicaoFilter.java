package com.api.gateway.config;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequisicaoFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = request.getHeader("X-Request-Id");
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put("requestId", id.trim());
        response.setHeader("X-Request-Id", id.trim());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
