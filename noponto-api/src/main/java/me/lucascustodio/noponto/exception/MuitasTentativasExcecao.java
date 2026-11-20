package me.lucascustodio.noponto.exception;

import org.springframework.http.HttpStatus;

public class MuitasTentativasExcecao extends ExcecaoDominio {

    public MuitasTentativasExcecao() {
        super("Muitas tentativas em sequência. Aguarde um minuto antes de tentar novamente.",
                HttpStatus.TOO_MANY_REQUESTS);
    }
}
