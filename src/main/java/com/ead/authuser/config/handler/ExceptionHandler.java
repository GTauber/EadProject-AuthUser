package com.ead.authuser.config.handler;

import com.ead.authuser.models.entity.Response;
import com.ead.authuser.models.exceptions.ApplicationException;
import com.ead.authuser.models.exceptions.BaseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
@Slf4j
@Order(-2)
public class ExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;
    private final DataBufferFactory dataBufferFactory;

    @Override
    @NonNull
    public Mono<Void> handle(@NonNull ServerWebExchange exchange, @NonNull Throwable throwable) {
        try {
            return this.handleException(exchange, error -> {
                exchange.getResponse().setStatusCode(HttpStatus.resolve(error.getStatus().value()));
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return exchange.getResponse()
                    .writeWith(Mono.just(dataBufferFactory.wrap(objectMapper.writeValueAsBytes(error))));
            }, throwable);

        } catch (JsonProcessingException ex) {
            log.error("Error mapping exception in the request [{}]", exchange.getRequest().getPath().value());
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }

    private Mono<Void> handleException(ServerWebExchange exchange, ErrorParserInterface parser, Throwable throwable)
        throws JsonProcessingException {
        this.logError(exchange, throwable);
        return Mono.from(parser.parse(createErrorResponse(throwable)));
    }

    private Response<?> createErrorResponse(Throwable throwable) {

        if (throwable instanceof WebExchangeBindException ex) {
            return buildValidationErrorResponse(this.getValidationErrors(ex));
        }
        if (throwable instanceof BaseException ex) {
            return buildResponse(ex);
        }

        return buildResponse(new ApplicationException());
    }

    private Response<?> buildValidationErrorResponse(List<String> validationErrors) {
        return Response.builder()
            .timestamp(LocalDateTime.now())
            .statusCode(HttpStatus.BAD_REQUEST.value())
            .status(HttpStatus.BAD_REQUEST)
            .reason(String.join(", ", validationErrors))
            .build();
    }

    private <T extends BaseException> Response<?> buildResponse(T throwable) {
        return Response.builder()
            .timestamp(LocalDateTime.now())
            .statusCode(throwable.getResponseCode().getStatus().value())
            .status(throwable.getResponseCode().getStatus())
            .reason(throwable.getMessage())
            .build();
    }

    protected List<String> getValidationErrors(WebExchangeBindException ex) {
        return ex.getAllErrors().stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .toList();
    }


/*    Complex mapping validation exception method: //TODO: Update this method.
    protected Map<String, String> getValidationErrors(WebExchangeBindException ex) {
        return ex.getAllErrors()
            .stream()
            .collect(Collectors.toMap(error -> {
                String errorCode = Objects.requireNonNull(error.getCode()).toLowerCase();
                if (error instanceof FieldError fieldError) {
                    return String.format("%s.%s", fieldError.getField(), errorCode);
                }
                return String.format("%s.%s", error.getObjectName(), errorCode);
            }, Objects.requireNonNull(DefaultMessageSourceResolvable::getDefaultMessage)));
    }
*/

    private void logError(ServerWebExchange exchange, Throwable throwable) {
        if (log.isErrorEnabled()) {
            log.error("The following error occurred in the call [{}] [{}]",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                throwable);
        }
    }
}
