package com.fraudshield.service;

import com.fraudshield.domain.Blocklist;
import com.fraudshield.repo.Repositories.BlocklistRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class BlocklistServiceTest {
    @Test
    void csvImportCountsAddedSkippedAndErrors() {
        BlocklistRepository repo = Mockito.mock(BlocklistRepository.class);
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(repo.findByKindAndValue("DOMAIN", "dup.test")).thenReturn(Optional.of(new Blocklist("DOMAIN", "dup.test", null)));
        String csv = String.join("\n",
            "kind,value,reason",
            "DOMAIN,https://Evil-One.test/path,phish",
            "DOMAIN,www.evil-two.test,phish",
            "DOMAIN,dup.test,already there",
            "DOMAIN,not a domain,bad",
            "FOO,x.test,bad kind",
            "DOMAIN");
        var r = new BlocklistService(repo, Mockito.mock(BlocklistSyncService.class)).importCsv(csv);
        assertEquals(2, r.added());
        assertEquals(4, r.skipped());
        assertEquals(4, r.errors().size());
    }

    @Test
    void normaliseStripsSchemePathWwwAndCase() {
        assertEquals("evil.test", BlocklistService.normalise("DOMAIN", " HTTPS://www.Evil.TEST:8080/a?b=1 "));
    }
}
