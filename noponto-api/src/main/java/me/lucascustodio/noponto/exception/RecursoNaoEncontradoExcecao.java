package me.lucascustodio.noponto.exception;

import org.springframework.http.HttpStatus;

public class RecursoNaoEncontradoExcecao extends ExcecaoDominio {

    public RecursoNaoEncontradoExcecao(String mensagem) {
        super(mensagem, HttpStatus.NOT_FOUND);
    }
}
