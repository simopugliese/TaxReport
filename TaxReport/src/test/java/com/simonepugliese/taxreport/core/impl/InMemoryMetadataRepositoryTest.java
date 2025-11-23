package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.domain.ExpenseEntry;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryMetadataRepositoryTest {

    @Test
    void testSaveAndFind() {
        InMemoryMetadataRepository repo = new InMemoryMetadataRepository();
        UUID id = UUID.randomUUID();
        ExpenseEntry entry = new ExpenseEntry(id, "cat1", LocalDate.now(), "Desc");

        // GIVEN: Save
        repo.save(entry);

        // WHEN: Find
        Optional<ExpenseEntry> found = repo.findById(id);

        // THEN
        assertTrue(found.isPresent());
        assertEquals(entry, found.get());
    }

    @Test
    void testUpdateStatus() {
        InMemoryMetadataRepository repo = new InMemoryMetadataRepository();
        UUID id = UUID.randomUUID();
        ExpenseEntry entry = new ExpenseEntry(id, "cat1", LocalDate.now(), "Desc");
        repo.save(entry);

        // WHEN
        repo.updateStatus(id, ValidationStatus.COMPLIANT);

        // THEN
        assertEquals(ValidationStatus.COMPLIANT, repo.findById(id).get().getStatus());
    }

    @Test
    void testFindMissing() {
        InMemoryMetadataRepository repo = new InMemoryMetadataRepository();
        Optional<ExpenseEntry> result = repo.findById(UUID.randomUUID());
        assertTrue(result.isEmpty());
    }
}