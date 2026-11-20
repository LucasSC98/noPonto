package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.NopontoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSegredoInicializacaoTest {

    private static final String MENSAGEM = "NOPONTO_JWT_SEGREDO ausente ou com menos de 32 bytes";
    private static final String SEGREDO_VALIDO = "noponto-teste-segredo-jwt-nao-usar-em-producao-01";

    @Test
    void deve_falharAoSubir_quando_segredoAusente() {
        assertThatThrownBy(() -> aplicacao().run("--noponto.jwt.segredo="))
                .hasRootCauseMessage(MENSAGEM);
    }

    @Test
    void deve_falharAoSubir_quando_segredoCurto() {
        assertThatThrownBy(() -> aplicacao().run("--noponto.jwt.segredo=curto"))
                .hasRootCauseMessage(MENSAGEM);
    }

    @Test
    void deve_subir_quando_segredoValido() {
        try (ConfigurableApplicationContext contexto = aplicacao().run(
                "--noponto.jwt.segredo=" + SEGREDO_VALIDO)) {
            assertThat(contexto.isActive()).isTrue();
            assertThat(contexto.getBean(JwtService.class)).isNotNull();
        }
    }

    private static SpringApplication aplicacao() {
        SpringApplication aplicacao = new SpringApplication(NopontoApplication.class);
        aplicacao.setWebApplicationType(WebApplicationType.NONE);
        return aplicacao;
    }
}
