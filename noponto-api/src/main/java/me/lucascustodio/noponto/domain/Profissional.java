package me.lucascustodio.noponto.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "profissional")
@Getter
@Setter
@NoArgsConstructor
public class Profissional extends EntidadeBase {

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 255)
    private String email;

    @Column(length = 80)
    private String especialidade;

    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private Boolean ativo = Boolean.TRUE;

    @OneToMany(mappedBy = "profissional")
    private List<ServicoItem> servicos = new ArrayList<>();
}
