package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.NopontoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class JwtSegredoInicializacaoTest {

    private static final String MENSAGEM = "NOPONTO_JWT_SEGREDO ausente ou com menos de 32 bytes";
    private static final String SEGREDO_VALIDO = "noponto-teste-segredo-jwt-nao-usar-em-producao-01";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Test
    void deve_falharAoSubir_quando_segredoAusente() {
        assertThatThrownBy(() -> aplicacao().run(argumentosComSegredo("")))
                .hasRootCauseMessage(MENSAGEM);
    }

    @Test
    void deve_falharAoSubir_quando_segredoCurto() {
        assertThatThrownBy(() -> aplicacao().run(argumentosComSegredo("curto")))
                .hasRootCauseMessage(MENSAGEM);
    }

    @Test
    void deve_subir_quando_segredoValido() {
        try (ConfigurableApplicationContext contexto = aplicacao().run(argumentosComSegredo(SEGREDO_VALIDO))) {
            assertThat(contexto.isActive()).isTrue();
            assertThat(contexto.getBean(JwtService.class)).isNotNull();
        }
    }

    private static String[] argumentosComSegredo(String segredo) {
        return new String[] {
                "--noponto.jwt.segredo=" + segredo,
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword()
        };
    }

    private static SpringApplication aplicacao() {
        SpringApplication aplicacao = new SpringApplication(NopontoApplication.class);
        aplicacao.setWebApplicationType(WebApplicationType.NONE);
        return aplicacao;
    }
}
