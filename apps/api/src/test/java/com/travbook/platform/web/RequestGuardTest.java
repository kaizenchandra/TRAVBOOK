package com.travbook.platform.web;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;
class RequestGuardTest {
    @Test void chunkedJsonUpdatesAreBoundedAndValidBodiesRemainReadable()throws Exception {
        var guard=new RequestGuard(JsonMapper.builder().build(),System::nanoTime);
        for(String method:java.util.List.of("POST","PUT","PATCH")) {
            var request=new MockHttpServletRequest(method,"/api/v1/me/profile") {
                @Override public long getContentLengthLong(){return -1;}
                @Override public int getContentLength(){return -1;}
            };
            request.setContentType("application/json");request.setContent(new byte[16_385]);
            var response=new MockHttpServletResponse();
            guard.doFilter(request,response,(r,s)->{throw new AssertionError("Oversized body reached the controller");});
            assertThat(response.getStatus()).isEqualTo(413);
        }
        var request=new MockHttpServletRequest("PUT","/api/v1/me/profile");request.setContentType("application/json");request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        guard.doFilter(request,new MockHttpServletResponse(),(r,s)->assertThat(r.getInputStream().readAllBytes()).isEqualTo("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void apiExhaustionCannotStarveLoginAndBothBudgetsRemainBounded() throws Exception {
        var time=new AtomicLong();var guard=new RequestGuard(JsonMapper.builder().build(),time::get);
        for(int i=0;i<240;i++)assertThat(request(guard,"/api/v1/locations").getStatus()).isEqualTo(200);
        var denied=request(guard,"/api/v1/locations");assertThat(denied.getStatus()).isEqualTo(429);assertThat(denied.getHeader("Retry-After")).isEqualTo("1");
        for(int i=0;i<120;i++)assertThat(request(guard,"/auth/session").getStatus()).isEqualTo(200);
        assertThat(request(guard,"/oauth2/authorization/travbook").getStatus()).isEqualTo(429);
        time.addAndGet(1_000_000_000L);
        for(int i=0;i<4;i++){assertThat(request(guard,"/auth/session").getStatus()).isEqualTo(200);assertThat(request(guard,"/api/v1/locations").getStatus()).isEqualTo(200);}
        assertThat(request(guard,"/auth/session").getStatus()).isEqualTo(429);assertThat(request(guard,"/api/v1/locations").getStatus()).isEqualTo(429);
    }
    private MockHttpServletResponse request(RequestGuard guard,String uri)throws Exception {
        var request=new MockHttpServletRequest("GET",uri);var response=new MockHttpServletResponse();
        guard.doFilter(request,response,(r,s)->((jakarta.servlet.http.HttpServletResponse)s).setStatus(200));return response;
    }
}
