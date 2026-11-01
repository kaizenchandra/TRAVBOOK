package com.travbook.notifications.adapter.in.web;
import com.travbook.notifications.application.port.in.Notifications;
import com.travbook.notifications.domain.model.Notice;
import com.travbook.platform.security.AuthenticatedIdentity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/me/notifications")
public class NotificationController {
    private final Notifications notifications;
    public NotificationController(Notifications notifications){this.notifications=notifications;}
    @GetMapping public Notifications.Page list(Authentication auth,@RequestParam(required=false)UUID after){return notifications.list(AuthenticatedIdentity.from(auth),after);}
    @PutMapping("/{id}/read") public Notice read(Authentication auth,@PathVariable UUID id){return notifications.read(AuthenticatedIdentity.from(auth),id);}
}
