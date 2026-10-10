package com.example.SpringRateLimitFilter.Filters;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    private final int MAX_REQUESTS = 5;
    private final long TIME_WINDOW = 10 * 1000L;

    private Map<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();

    private static final int SC_TOO_MANY_REQUESTS = 429;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String ipAddress = httpRequest.getRemoteAddr();

        long currentTime = Instant.now().toEpochMilli();
        requestCounts.putIfAbsent(ipAddress, new RequestCounter());

        RequestCounter counter = requestCounts.get(ipAddress);
        counter.cleanOldRequests(currentTime, TIME_WINDOW);

        if (counter.getCount() >= MAX_REQUESTS) {
            httpResponse.setStatus(SC_TOO_MANY_REQUESTS);
            httpResponse.getWriter().write("Too many requests! Please try again later.");
            return;
        }

        counter.addRequest(currentTime);
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }

    private static class RequestCounter {
        private final ConcurrentHashMap<Long, Long> timestamps = new ConcurrentHashMap<>();

        synchronized void addRequest(long currentTime) {
            timestamps.put(currentTime, currentTime);
        }

        synchronized void cleanOldRequests(long currentTime, long timeWindow) {
            timestamps.keySet().removeIf(timestamp -> (currentTime - timestamp) > timeWindow);
        }

        int getCount() {
            return timestamps.size();
        }
    }
}