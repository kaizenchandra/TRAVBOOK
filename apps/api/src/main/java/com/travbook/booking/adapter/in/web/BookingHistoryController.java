package com.travbook.booking.adapter.in.web;
import com.travbook.booking.application.port.in.BookingHistory;
import com.travbook.platform.security.AuthenticatedIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
public class BookingHistoryController {
    private final BookingHistory history;
    public BookingHistoryController(BookingHistory history){this.history=history;}
    @GetMapping("/api/v1/bookings/{id}/timeline")
    public BookingHistory.Page get(Authentication auth,@PathVariable UUID id,@RequestParam(defaultValue="0") long afterVersion,@RequestParam(defaultValue="20") int size){
        return history.get(AuthenticatedIdentity.from(auth),id,afterVersion,size);
    }
}
