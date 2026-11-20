package me.lucascustodio.noponto.exception;

import me.lucascustodio.noponto.dto.ConflitoBloqueioResponse;
import me.lucascustodio.noponto.dto.ErroResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class TratadorGlobalExcecoes {

    @ExceptionHandler(ConflitoBloqueioExcecao.class)
    public ResponseEntity<ConflitoBloqueioResponse> tratarConflitoBloqueio(ConflitoBloqueioExcecao excecao) {
        ConflitoBloqueioResponse corpo = new ConflitoBloqueioResponse(
                excecao.getStatus().value(),
                excecao.getMessage(),
                OffsetDateTime.now(ZoneOffset.UTC),
                excecao.getAgendamentosConflitantes());
        return ResponseEntity.status(excecao.getStatus()).body(corpo);
    }

    @ExceptionHandler(ExcecaoDominio.class)
    public ResponseEntity<ErroResponse> tratarExcecaoDominio(ExcecaoDominio excecao) {
        return responder(excecao.getStatus(), excecao.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> tratarValidacao(MethodArgumentNotValidException excecao) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError erro : excecao.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(erro.getField(), erro.getDefaultMessage());
        }
        return responder(HttpStatus.BAD_REQUEST, "Dados da requisição inválidos.", campos);
    }

    private ResponseEntity<ErroResponse> responder(HttpStatus status, String mensagem, Map<String, String> campos) {
        ErroResponse corpo = new ErroResponse(status.value(), mensagem, OffsetDateTime.now(ZoneOffset.UTC), campos);
        return ResponseEntity.status(status).body(corpo);
    }
}
