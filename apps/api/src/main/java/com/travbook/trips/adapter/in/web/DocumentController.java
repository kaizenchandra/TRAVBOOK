package com.travbook.trips.adapter.in.web;
import com.travbook.trips.application.port.in.TripDocuments;
import com.travbook.trips.domain.model.TripDocument;
import com.travbook.platform.security.AuthenticatedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
@RestController
public class DocumentController {
    private final TripDocuments documents;
    public DocumentController(TripDocuments documents){this.documents=documents;}
    public enum Kind { MOCK_CONFIRMATION_SUMMARY }
    public record Request(@NotNull Kind kind) {}
    public record Metadata(UUID id,UUID bookingId,UUID checkoutId,long checkoutVersion,String kind,String mode,Instant createdAt,String mediaType,String sha256,int byteLength,String downloadUrl){
        static Metadata of(TripDocument d){return new Metadata(d.id(),d.bookingId(),d.checkoutId(),d.checkoutVersion(),TripDocument.KIND,"MOCK",d.createdAt(),TripDocument.MEDIA_TYPE,d.sha256(),d.bytes().length,"/api/v1/bookings/"+d.bookingId()+"/documents/"+d.id()+"/content");}
    }
    public record ListResponse(List<Metadata> items) {}
    @GetMapping("/api/v1/bookings/{id}/documents")
    public ListResponse list(Authentication auth,@PathVariable UUID id){return new ListResponse(documents.list(AuthenticatedIdentity.from(auth),id).stream().map(Metadata::of).toList());}
    @PostMapping("/api/v1/bookings/{id}/documents")
    public ResponseEntity<Metadata> create(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Request body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request){
        var d=documents.create(AuthenticatedIdentity.from(auth),id,key,UUID.fromString((String)request.getAttribute("correlationId")));var metadata=Metadata.of(d);
        return ResponseEntity.created(URI.create(metadata.downloadUrl())).body(metadata);
    }
    @GetMapping("/api/v1/bookings/{id}/documents/{documentId}/content")
    public ResponseEntity<byte[]> content(Authentication auth,@PathVariable UUID id,@PathVariable UUID documentId){
        var d=documents.download(AuthenticatedIdentity.from(auth),id,documentId);byte[] bytes=d.bytes();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(TripDocument.MEDIA_TYPE)).contentLength(bytes.length)
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"travbook-MOCK-summary-"+d.id()+".txt\"")
            .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").body(bytes);
    }
}
