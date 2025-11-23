package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.domain.ExpenseEntry;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import com.simonepugliese.taxreport.core.spi.MetadataRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMetadataRepository implements MetadataRepository {
    private final Map<UUID, ExpenseEntry> db = new ConcurrentHashMap<>();

    @Override
    public void save(ExpenseEntry entry) {
        db.put(entry.getId(), entry);
    }

    @Override
    public Optional<ExpenseEntry> findById(UUID id) {
        return Optional.ofNullable(db.get(id));
    }

    @Override
    public void updateStatus(UUID id, ValidationStatus status) {
        ExpenseEntry e = db.get(id);
        if (e != null) e.setStatus(status);
    }
}
