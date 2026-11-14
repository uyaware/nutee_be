package com.nutee.backend.catalog.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.nutee.backend.user.config.LocalAccountSeedRunner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class CatalogSeedRunnerTest {
    @Test
    void existingDataPreventsCatalogAndAccountSeeding() {
        DataSource dataSource = mock(DataSource.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        LocalAccountSeedRunner accounts = mock(LocalAccountSeedRunner.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn("", true);

        new CatalogSeedRunner(dataSource, entityManager, accounts).run(null);

        verifyNoInteractions(dataSource, accounts);
    }
}
