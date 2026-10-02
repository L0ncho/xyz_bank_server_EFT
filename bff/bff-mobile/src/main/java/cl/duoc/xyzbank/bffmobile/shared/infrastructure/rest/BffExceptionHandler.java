package cl.duoc.xyzbank.bffmobile.shared.infrastructure.rest;

import cl.duoc.xyzbank.bffmobile.shared.infrastructure.adapters.CoreServiceCallException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BffExceptionHandler {

    @ExceptionHandler(CallerIdentityException.class)
    public ProblemDetail handleCallerIdentity(CallerIdentityException exception) {
        HttpStatus status = exception.getType() == CallerIdentityException.Type.INVALID
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.FORBIDDEN;
        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    }

    @ExceptionHandler(CoreServiceCallException.class)
    public ProblemDetail handleCoreServiceCall(CoreServiceCallException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(exception.getStatus()), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidRequest(MethodArgumentNotValidException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request is invalid");
    }
}
