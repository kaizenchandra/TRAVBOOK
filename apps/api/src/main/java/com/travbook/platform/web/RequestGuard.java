package com.travbook.platform.web;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.UUID;
/** Single-instance public-discovery guard. Edge/distributed controls remain a release gate. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestGuard extends OncePerRequestFilter {
    private final ObjectMapper json;
    private final Bucket apiBudget,identityBudget;
    @org.springframework.beans.factory.annotation.Autowired
    public RequestGuard(ObjectMapper json) { this(json,System::nanoTime); }
    RequestGuard(ObjectMapper json,java.util.function.LongSupplier clock) {
        this.json=json;apiBudget=new Bucket(240,clock);identityBudget=new Bucket(120,clock);
    }
    private static final class Bucket {
        private final int capacity;private final java.util.function.LongSupplier clock;private double tokens;private long last;
        Bucket(int capacity,java.util.function.LongSupplier clock){this.capacity=capacity;this.clock=clock;tokens=capacity;last=clock.getAsLong();}
        synchronized boolean take() {
            long now=clock.getAsLong();tokens=Math.min(capacity,tokens+(now-last)/1_000_000_000.0*4);last=now;
            if(tokens<1)return false;tokens--;return true;
        }
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String correlation=UUID.randomUUID().toString();
        request.setAttribute("correlationId",correlation);
        response.setHeader("X-Correlation-ID",correlation);
        response.setHeader("Cache-Control","no-store");
        boolean api=request.getRequestURI().startsWith("/api/");
        boolean identity=java.util.stream.Stream.of("/auth/","/oauth2/","/login/").anyMatch(request.getRequestURI()::startsWith);
        if((api&&!apiBudget.take())||(identity&&!identityBudget.take())) {
            response.setHeader("Retry-After","1");
            reject(response,429,"RATE_LIMITED","Please retry shortly.",correlation); return;
        }
        if(request.getContentLengthLong()>16_384) { reject(response,413,"REQUEST_TOO_LARGE","Request exceeds 16 KiB.",correlation); return; }
        if(java.util.Set.of("POST","PUT","PATCH").contains(request.getMethod()) && request.getContentType()!=null && request.getContentType().startsWith("application/json")) {
            byte[] bytes=request.getInputStream().readNBytes(16_385);
            if(bytes.length>16_384) { reject(response,413,"REQUEST_TOO_LARGE","Request exceeds 16 KiB.",correlation); return; }
            var wrapped=new HttpServletRequestWrapper(request) {
                @Override public ServletInputStream getInputStream() {
                    var input=new ByteArrayInputStream(bytes);
                    return new ServletInputStream() {
                        public int read() { return input.read(); }
                        public boolean isFinished() { return input.available()==0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                    };
                }
                @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
            };
            chain.doFilter(wrapped,response); return;
        }
        chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response,int status,String code,String detail,String correlation) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        response.getWriter().write(json.writeValueAsString(ProblemAdvice.body(status,code,detail,correlation)));
    }
}

