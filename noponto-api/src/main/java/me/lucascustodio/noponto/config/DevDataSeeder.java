package me.lucascustodio.noponto.config;

import lombok.extern.slf4j.Slf4j;
import me.lucascustodio.noponto.domain.PerfilUsuario;
import me.lucascustodio.noponto.domain.Profissional;
import me.lucascustodio.noponto.domain.RegraDisponibilidade;
import me.lucascustodio.noponto.domain.ServicoItem;
import me.lucascustodio.noponto.domain.Tenant;
import me.lucascustodio.noponto.domain.Usuario;
import me.lucascustodio.noponto.repository.ProfissionalRepository;
import me.lucascustodio.noponto.repository.RegraDisponibilidadeRepository;
import me.lucascustodio.noponto.repository.ServicoItemRepository;
import me.lucascustodio.noponto.repository.TenantRepository;
import me.lucascustodio.noponto.repository.UsuarioRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final List<DayOfWeek> DIAS_UTEIS = List.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY);

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ServicoItemRepository servicoItemRepository;
    private final RegraDisponibilidadeRepository regraDisponibilidadeRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(TenantRepository tenantRepository,
                         UsuarioRepository usuarioRepository,
                         ProfissionalRepository profissionalRepository,
                         ServicoItemRepository servicoItemRepository,
                         RegraDisponibilidadeRepository regraDisponibilidadeRepository,
                         PasswordEncoder passwordEncoder) {
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.profissionalRepository = profissionalRepository;
        this.servicoItemRepository = servicoItemRepository;
        this.regraDisponibilidadeRepository = regraDisponibilidadeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (tenantRepository.count() > 0) {
            return;
        }

        Tenant tenant = new Tenant();
        tenant.setNome("Clínica Teste");
        tenant.setSubdominio("clinica-teste");
        tenant.setAtivo(Boolean.TRUE);
        tenant = tenantRepository.save(tenant);

        Usuario admin = new Usuario();
        admin.setTenantId(tenant.getId());
        admin.setNome("Admin Teste");
        admin.setEmail("admin@teste.com");
        admin.setSenhaHash(passwordEncoder.encode("123456"));
        admin.setRole(PerfilUsuario.ADMIN);
        admin.setAtivo(Boolean.TRUE);
        usuarioRepository.save(admin);

        Profissional profissional = new Profissional();
        profissional.setTenantId(tenant.getId());
        profissional.setNome("Dr. João Silva");
        profissional.setEmail("joao@clinica.com");
        profissional.setEspecialidade("Clínico Geral");
        profissional.setAtivo(Boolean.TRUE);
        profissional = profissionalRepository.save(profissional);

        ServicoItem consultaGeral = criarServico(tenant, profissional, "Consulta Geral", 30);
        ServicoItem retorno = criarServico(tenant, profissional, "Retorno", 15);

        OffsetTime horaInicio = OffsetTime.of(9, 0, 0, 0, ZoneOffset.ofHours(-3));
        OffsetTime horaFim = OffsetTime.of(18, 0, 0, 0, ZoneOffset.ofHours(-3));
        for (DayOfWeek dia : DIAS_UTEIS) {
            RegraDisponibilidade regra = new RegraDisponibilidade();
            regra.setTenantId(tenant.getId());
            regra.setProfissional(profissional);
            regra.setDiaSemana(dia);
            regra.setHoraInicio(horaInicio);
            regra.setHoraFim(horaFim);
            regra.setAtivo(Boolean.TRUE);
            regraDisponibilidadeRepository.save(regra);
        }

        log.info("✅ Seed concluído: tenant clinica-teste, admin@teste.com / 123456");
        log.info("Dados dev criados -> profissionalId={}, consultaGeralId={}, retornoId={}",
                profissional.getId(), consultaGeral.getId(), retorno.getId());
    }

    private ServicoItem criarServico(Tenant tenant, Profissional profissional, String nome, int duracaoMinutos) {
        ServicoItem servico = new ServicoItem();
        servico.setTenantId(tenant.getId());
        servico.setProfissional(profissional);
        servico.setNome(nome);
        servico.setDuracaoMinutos(duracaoMinutos);
        servico.setAtivo(Boolean.TRUE);
        return servicoItemRepository.save(servico);
    }
}
