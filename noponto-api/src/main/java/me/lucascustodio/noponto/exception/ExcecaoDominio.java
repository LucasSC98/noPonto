package me.lucascustodio.noponto.exception;

import org.springframework.http.HttpStatus;

/**
 * Base de todas as exceções de domínio do NoPonto.
 * Cada subclasse define o status HTTP que o handler global deve retornar.
 */
public abstract class ExcecaoDominio extends RuntimeException {

    private final HttpStatus status;

    protected ExcecaoDominio(String mensagem, HttpStatus status) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
