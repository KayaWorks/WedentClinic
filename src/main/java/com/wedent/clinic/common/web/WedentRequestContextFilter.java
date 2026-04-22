package com.wedent.clinic.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates the logging MDC with request-scoped identifiers so every log line
 * emitted while handling a request can be correlated.
 *
 * <ul>
 *   <li>{@code traceId} — propagated from {@code X-Request-Id} header if the
 *       caller supplied one, otherwise a fresh UUID. Echoed back in the
 *       response header so clients can report it in bug tickets.</li>
 *   <li>{@code method}, {@code path} — for grep-friendly logs.</li>
 * </ul>
 *
 * Ordered just after Spring's built-in context filters so every business log
 * line inherits the MDC, including those emitted from the security filter chain.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class WedentRequestContextFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Request-Id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_METHOD = "method";
    public static final String MDC_PATH = "path";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER));
        try {
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_METHOD, request.getMethod());
            MDC.put(MDC_PATH, request.getRequestURI());
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_PATH);
        }
    }

    private String resolveTraceId(String incoming) {
        if (StringUtils.hasText(incoming) && incoming.length() <= 128) {
            // Accept caller-supplied id so traces span across services/gateways,
            // but length-cap to prevent log-poisoning via huge headers.
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }
}
