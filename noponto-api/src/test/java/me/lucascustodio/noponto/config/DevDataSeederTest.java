package me.lucascustodio.noponto.config;

import me.lucascustodio.noponto.domain.Bloqueio;
import me.lucascustodio.noponto.domain.PerfilUsuario;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.domain.Usuario;
import me.lucascustodio.noponto.repository.BloqueioRepository;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.RegraDisponibilidadeRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import me.lucascustodio.noponto.repository.TenantRepository;
import me.lucascustodio.noponto.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DevDataSeederTest {

    private TenantRepository tenantRepository;
    private UsuarioRepository usuarioRepository;
    private ProfissionalRepository profissionalRepository;
    private ServicoItemRepository servicoItemRepository;
    private RegraDisponibilidadeRepository regraRepository;
    private BloqueioRepository bloqueioRepository;
    private PasswordEncoder passwordEncoder;
    private DevDataSeeder seeder;

    @BeforeEach
    void configurar() {
        tenantRepository = mock(TenantRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        profissionalRepository = mock(ProfissionalRepository.class);
        servicoItemRepository = mock(ServicoItemRepository.class);
        regraRepository = mock(RegraDisponibilidadeRepository.class);
        bloqueioRepository = mock(BloqueioRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        Clock relogio = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
        seeder = new DevDataSeeder(
                tenantRepository,
                usuarioRepository,
                profissionalRepository,
                servicoItemRepository,
                regraRepository,
                bloqueioRepository,
                passwordEncoder,
                relogio);
    }

    @Test
    void deve_naoInserirDados_quando_jaExisteTenantNoBanco() {
        when(tenantRepository.count()).thenReturn(1L);

        seeder.run(new DefaultApplicationArguments());

        verify(tenantRepository).count();
        verifyNoInteractions(usuarioRepository, profissionalRepository, servicoItemRepository,
                regraRepository, bloqueioRepository);
    }

    @Test
    void deve_popularDadosIniciaisComSenhaHasheada_quando_bancoVazio() {
        when(tenantRepository.count()).thenReturn(0L);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(inv -> {
            Profissional p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(servicoItemRepository.save(any(ServicoItem.class))).thenAnswer(inv -> {
            ServicoItem s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        seeder.run(new DefaultApplicationArguments());

        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuarioCaptor.capture());
        Usuario admin = usuarioCaptor.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@teste.com");
        assertThat(admin.getRole()).isEqualTo(PerfilUsuario.ADMIN);
        assertThat(admin.getAtivo()).isTrue();
        assertThat(admin.getSenhaHash()).isNotEqualTo("123456");
        assertThat(passwordEncoder.matches("123456", admin.getSenhaHash())).isTrue();

        verify(servicoItemRepository, times(2)).save(any(ServicoItem.class));
        verify(regraRepository, times(5)).save(any(RegraDisponibilidade.class));

        ArgumentCaptor<Bloqueio> bloqueioCaptor = ArgumentCaptor.forClass(Bloqueio.class);
        verify(bloqueioRepository).save(bloqueioCaptor.capture());
        Bloqueio bloqueio = bloqueioCaptor.getValue();
        assertThat(bloqueio.getTenantId()).isNotNull();
        assertThat(bloqueio.getProfissional().getNome()).isEqualTo("Dr. João Silva");
        assertThat(bloqueio.getMotivo()).isEqualTo("Feriado de Natal");
        assertThat(bloqueio.getInicio()).isEqualTo(OffsetDateTime.parse("2026-12-25T03:00:00Z"));
        assertThat(bloqueio.getFim()).isEqualTo(OffsetDateTime.parse("2026-12-26T03:00:00Z"));
    }
}
