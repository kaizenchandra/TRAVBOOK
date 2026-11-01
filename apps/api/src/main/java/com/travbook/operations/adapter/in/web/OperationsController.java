package com.travbook.operations.adapter.in.web;
import com.travbook.operations.application.port.in.Operations;
import com.travbook.shared.domain.Recovery.*;
import com.travbook.platform.security.OperatorIdentity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;import java.util.*;
@RestController public class OperationsController {
 private static void legacyKind(Kind k){if(k!=Kind.HOTEL&&k!=Kind.RAIL)throw new com.travbook.shared.domain.DomainFailure(com.travbook.shared.domain.DomainFailure.Code.VALIDATION_FAILED,"Journey categories use the v2 operations API.");}
 private final Operations operations;public OperationsController(Operations operations){this.operations=operations;}
 public record Resume(@Min(1) long version,@NotNull Reason reason,@AssertTrue boolean consent){}
 @GetMapping("/api/v1/operations/reviews/{kind}") public List<Case> list(Authentication a,@PathVariable Kind kind,@RequestParam(defaultValue="0") int page){legacyKind(kind);OperatorIdentity.require(a,Instant.now());return operations.list(kind,page);}
 @GetMapping("/api/v1/operations/reviews/{kind}/{id}") public Case get(Authentication a,@PathVariable Kind kind,@PathVariable UUID id){legacyKind(kind);OperatorIdentity.require(a,Instant.now());return operations.get(kind,id);}
 @PostMapping("/api/v1/operations/reviews/{kind}/{id}/resume") @ResponseStatus(HttpStatus.ACCEPTED) public Action resume(Authentication a,@PathVariable Kind kind,@PathVariable UUID id,@Valid @RequestBody Resume body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request){legacyKind(kind);return operations.resume(kind,id,body.version(),OperatorIdentity.require(a,Instant.now()),body.reason(),body.consent(),key,UUID.fromString((String)request.getAttribute("correlationId")));}
 @PostMapping("/api/v1/operations/reviews/{kind}/{id}/reconcile-legacy") @ResponseStatus(HttpStatus.ACCEPTED) public Action reconcileLegacy(Authentication a,@PathVariable Kind kind,@PathVariable UUID id,@Valid @RequestBody Resume body,@RequestHeader("Idempotency-Key") String key,HttpServletRequest request){legacyKind(kind);return operations.reconcileLegacy(kind,id,body.version(),OperatorIdentity.require(a,Instant.now()),body.reason(),body.consent(),key,UUID.fromString((String)request.getAttribute("correlationId")));}
}
