package com.smartlearnly.backend.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuditMigrationContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V11__system_activity_audit_log.sql"
    );

    @Test
    void migrationShouldContainRequiredSecurityAndAppendOnlyContract() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE public.audit_logs")
                .contains("id uuid PRIMARY KEY DEFAULT gen_random_uuid()")
                .contains("old_values jsonb")
                .contains("new_values jsonb")
                .contains("metadata jsonb")
                .contains("SET search_path = ''")
                .contains("BEFORE UPDATE OR DELETE ON public.audit_logs")
                .contains("ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY")
                .contains("REVOKE ALL ON TABLE public.audit_logs FROM anon, authenticated")
                .doesNotContain("CREATE POLICY");
    }

    @Test
    void migrationShouldContainAllRequiredIndexes() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("idx_audit_logs_occurred_at")
                .contains("idx_audit_logs_domain_time")
                .contains("idx_audit_logs_action_time")
                .contains("idx_audit_logs_actor_time")
                .contains("idx_audit_logs_result_time")
                .contains("idx_audit_logs_target")
                .contains("idx_audit_logs_correlation");
    }
}
