package org.kodvido.app;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/** Reject DNS rebinding Host values even though the socket binds only to loopback. */
@Component
public class LocalHostFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!Set.of("localhost", "127.0.0.1", "[::1]").contains(request.getServerName())) {
            response.sendError(403); return;
        }
        chain.doFilter(request, response);
    }
}
