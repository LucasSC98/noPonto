package me.lucascustodio.noponto.exception;

import org.springframework.http.HttpStatus;

public class RegraNegocioExcecao extends ExcecaoDominio {

    public RegraNegocioExcecao(String mensagem) {
        super(mensagem, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
