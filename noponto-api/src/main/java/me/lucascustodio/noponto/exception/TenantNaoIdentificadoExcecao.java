package me.lucascustodio.noponto.exception;

import org.springframework.http.HttpStatus;

public class TenantNaoIdentificadoExcecao extends ExcecaoDominio {

    public TenantNaoIdentificadoExcecao() {
        super("Não foi possível identificar o estabelecimento da requisição.", HttpStatus.BAD_REQUEST);
    }
}
