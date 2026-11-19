package me.lucascustodio.noponto.service;

import me.lucascustodio.noponto.domain.Usuario;
import me.lucascustodio.noponto.dto.LoginRequest;
import me.lucascustodio.noponto.dto.LoginResponse;
import me.lucascustodio.noponto.exception.CredenciaisInvalidasExcecao;
import me.lucascustodio.noponto.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long expiracaoSegundos;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${noponto.jwt.expiracao-minutos}") long expiracaoMinutos) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.expiracaoSegundos = expiracaoMinutos * 60;
    }

    @Transactional(readOnly = true)
    public LoginResponse autenticar(LoginRequest requisicao) {
        List<Usuario> candidatos = usuarioRepository.findAllByEmailIgnoreCaseAndAtivoTrue(requisicao.email().trim());

        List<Usuario> autenticados = candidatos.stream()
                .filter(usuario -> passwordEncoder.matches(requisicao.senha(), usuario.getSenhaHash()))
                .toList();

        // E-mail inexistente, senha errada ou o mesmo par e-mail/senha válido em mais de um tenant:
        // em todos os casos a resposta é genérica para não revelar quais contas existem.
        if (autenticados.size() != 1) {
            throw new CredenciaisInvalidasExcecao();
        }

        String token = jwtService.gerarToken(autenticados.getFirst());
        return new LoginResponse(token, LoginResponse.TIPO_BEARER, expiracaoSegundos);
    }
}
