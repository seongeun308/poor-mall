package io.github.seongeun308.poormall.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)  // 트레이싱 필터 이후에 실행
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            // todo: 시큐리티 적용 후 활성화
//            currentUserId().ifPresent(id -> MDC.put("userId", id));
            response.setHeader("X-Trace-Id", MDC.get("traceId"));
            chain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }
}
