package me.lucascustodio.noponto.exception;

import org.springframework.http.HttpStatus;

/** Violação de regra de negócio (transição de status inválida, cancelamento fora do prazo etc.). */
public class RegraNegocioExcecao extends ExcecaoDominio {

    public RegraNegocioExcecao(String mensagem) {
        super(mensagem, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
