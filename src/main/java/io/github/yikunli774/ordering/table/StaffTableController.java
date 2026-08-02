package io.github.yikunli774.ordering.table;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Staff-only: list tables with the signed token each QR code should encode, so a
 * manager can print them. Not permitted in SecurityConfig, so it requires a valid
 * staff access token — the first business endpoint guarded by staff auth.
 */
@RestController
@RequestMapping("/api/v1/staff/tables")
public class StaffTableController {

    private final TableSessionRepository repository;
    private final TableCodeSigner signer;

    public StaffTableController(TableSessionRepository repository, TableCodeSigner signer) {
        this.repository = repository;
        this.signer = signer;
    }

    @GetMapping
    public List<TableQr> list() {
        return repository.findAllTables().stream()
                .map(table -> new TableQr(table.code(), table.name(), signer.sign(table.code())))
                .toList();
    }

    public record TableQr(String code, String name, String qrToken) {
    }
}
