package fmi.ethnowear.persistence.jdbc.document;

import fmi.ethnowear.domain.model.document.processing.JobType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlServerWorkerJobClaimStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void bindsClaimTokenHashAsChar() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SqlParameterSource[] captured = new SqlParameterSource[1];

        when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    captured[0] = invocation.getArgument(1);
                    return List.of();
                });

        LocalDateTime now = LocalDateTime.now();
        new SqlServerWorkerJobClaimStore(jdbc).claimNext(
                Set.of(JobType.OCR),
                "worker-1",
                now,
                now.plusMinutes(1),
                now.plusMinutes(5),
                "a".repeat(64)
        );

        assertEquals(Types.CHAR, captured[0].getSqlType("claimTokenHash"));
    }
}
