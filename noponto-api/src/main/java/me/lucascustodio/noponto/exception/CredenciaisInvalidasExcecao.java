package me.lucascustodio.noponto.exception;

import org.springframework.http.HttpStatus;

public class CredenciaisInvalidasExcecao extends ExcecaoDominio {

    public CredenciaisInvalidasExcecao() {
        super("E-mail ou senha inválidos.", HttpStatus.UNAUTHORIZED);
    }
}
