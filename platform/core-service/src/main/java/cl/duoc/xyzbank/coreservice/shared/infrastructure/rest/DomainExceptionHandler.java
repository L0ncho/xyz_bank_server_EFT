package cl.duoc.xyzbank.coreservice.shared.infrastructure.rest;

import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DomainExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException exception) {
        HttpStatus status = switch (exception.getType()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case VALIDATION -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CONFLICT -> HttpStatus.CONFLICT;
            case OTHER -> HttpStatus.BAD_REQUEST;
        };

        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidRequest(MethodArgumentNotValidException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request is invalid");
    }
}
