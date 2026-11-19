package me.lucascustodio.noponto.exception;

import org.springframework.http.HttpStatus;

public class HorarioIndisponivelExcecao extends ExcecaoDominio {

    public HorarioIndisponivelExcecao() {
        super("O horário selecionado não está mais disponível.", HttpStatus.CONFLICT);
    }
}
