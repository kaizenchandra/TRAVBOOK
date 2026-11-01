package com.travbook.operations.adapter.in.web;
import com.travbook.operations.application.port.in.Operations;
import com.travbook.shared.domain.Recovery.*;
import com.travbook.shared.domain.DomainFailure;
import com.travbook.platform.security.OperatorIdentity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
@RestController public class JourneyOperationsController {
 private final Operations operations;
 public JourneyOperationsController(Operations operations){this.operations=operations;}
 private static void kind(Kind k){if(k!=Kind.BUS&&k!=Kind.HOLIDAY&&k!=Kind.CAB)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Hotel and rail categories retain their v1 operations API.");}
 @GetMapping("/api/v2/operations/reviews/{kind}") public List<Case> list(Authentication a,@PathVariable Kind kind,@RequestParam(defaultValue="0") int page){kind(kind);OperatorIdentity.require(a,Instant.now());return operations.list(kind,page);}
 @GetMapping("/api/v2/operations/reviews/{kind}/{id}") public Case get(Authentication a,@PathVariable Kind kind,@PathVariable UUID id){kind(kind);OperatorIdentity.require(a,Instant.now());return operations.get(kind,id);}
 @PostMapping("/api/v2/operations/reviews/{kind}/{id}/resume") @ResponseStatus(HttpStatus.ACCEPTED) public Action resume(Authentication a,@PathVariable Kind kind,@PathVariable UUID id,@Valid @RequestBody OperationsController.Resume body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request){kind(kind);return operations.resume(kind,id,body.version(),OperatorIdentity.require(a,Instant.now()),body.reason(),body.consent(),key,UUID.fromString((String)request.getAttribute("correlationId")));}
}
