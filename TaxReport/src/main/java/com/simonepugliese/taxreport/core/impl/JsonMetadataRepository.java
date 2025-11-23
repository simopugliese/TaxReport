package com.simonepugliese.taxreport.core.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.simonepugliese.taxreport.core.domain.ExpenseEntry;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import com.simonepugliese.taxreport.core.exception.StorageException;
import com.simonepugliese.taxreport.core.spi.MetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JsonMetadataRepository implements MetadataRepository {
    private static final Logger logger = LoggerFactory.getLogger(JsonMetadataRepository.class);

    private final File dbFile;
    private final ObjectMapper mapper;
    // Cache in memoria
    private final Map<UUID, ExpenseEntry> cache = new ConcurrentHashMap<>();

    public JsonMetadataRepository(String folderPath) {
        this.dbFile = new File(folderPath, "db_expenses.json");
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        loadFromFile();
    }

    private void loadFromFile() {
        if (!dbFile.exists()) {
            logger.info("DB file non trovato. Sarà creato al primo salvataggio: {}", dbFile.getAbsolutePath());
            return;
        }
        try {
            Map<UUID, ExpenseEntry> loaded = mapper.readValue(dbFile, new TypeReference<>() {});
            cache.putAll(loaded);
            logger.info("Caricate {} spese dal DB JSON.", cache.size());
        } catch (IOException e) {
            logger.error("Database corrotto o illeggibile", e);
            throw new StorageException("Impossibile leggere il database JSON", e);
        }
    }

    // Synchronized per evitare corruzione del file se due thread salvano insieme
    private synchronized void saveToFile() {
        try {
            mapper.writeValue(dbFile, cache);
        } catch (IOException e) {
            throw new StorageException("Impossibile salvare il database JSON", e);
        }
    }

    @Override
    public void save(ExpenseEntry entry) {
        cache.put(entry.getId(), entry);
        saveToFile();
    }

    @Override
    public Optional<ExpenseEntry> findById(UUID id) {
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public void updateStatus(UUID id, ValidationStatus status) {
        ExpenseEntry entry = cache.get(id);
        if (entry != null) {
            entry.setStatus(status);
            saveToFile();
        }
    }
}