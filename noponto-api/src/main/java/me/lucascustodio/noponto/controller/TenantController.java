package me.lucascustodio.noponto.controller;

import me.lucascustodio.noponto.dto.TenantAtualResponse;
import me.lucascustodio.noponto.service.TenantService;
import me.lucascustodio.noponto.tenant.TenantContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/tenants/current")
    public TenantAtualResponse atual() {
        String subdominio = tenantService.buscarPorId(TenantContextHolder.get()).getSubdominio();
        return new TenantAtualResponse(subdominio);
    }
}
