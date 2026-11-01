package com.travbook.platform.web;
import com.travbook.shared.domain.DomainFailure;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;
@RestControllerAdvice
public class ProblemAdvice {
    static Map<String,Object> body(int status,String code,String detail,String correlation) {
        return Map.of("type","urn:travbook:problem:"+code.toLowerCase(java.util.Locale.ROOT),"title",code,"status",status,"detail",detail,"code",code,"correlationId",correlation);
    }
    @ExceptionHandler(DomainFailure.class) ResponseEntity<?> domain(DomainFailure e,HttpServletRequest request) {
        int status=switch(e.code()) {
            case NOT_FOUND -> 404;
            case SEARCH_EXPIRED,OFFER_EXPIRED,OFFER_UNAVAILABLE,QUOTE_EXPIRED,QUOTE_VERSION_CONFLICT,IDEMPOTENCY_CONFLICT,QUOTE_NOT_ACCEPTED,BOOKING_CONFLICT,PROFILE_VERSION_CONFLICT,PROFILE_IDENTITY_CONFLICT -> 409;
            case SUPPLIERS_UNAVAILABLE -> 503;
            default -> 422;
        };
        return response(status,e.code().name(),e.getMessage(),request);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,org.springframework.web.bind.MissingRequestHeaderException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<?> invalid(Exception e,HttpServletRequest request) { return response(400,"INVALID_REQUEST","Check the request fields and formats.",request); }
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,org.springframework.web.servlet.NoHandlerFoundException.class})
    ResponseEntity<?> missing(Exception e,HttpServletRequest request) { return response(404,"NOT_FOUND","The requested resource was not found.",request); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> database(Exception e,HttpServletRequest request) { return response(503,"DEPENDENCY_UNAVAILABLE","The data service is temporarily unavailable. Please retry.",request); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e,HttpServletRequest request) { return response(500,"INTERNAL_ERROR","Unable to complete the request. Use the correlation ID when contacting support.",request); }
    private ResponseEntity<?> response(int status,String code,String detail,HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body(status,code,detail,String.valueOf(request.getAttribute("correlationId"))));
    }
}

