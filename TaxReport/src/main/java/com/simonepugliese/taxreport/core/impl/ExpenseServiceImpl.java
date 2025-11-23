package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.api.ExpenseService;
import com.simonepugliese.taxreport.core.domain.DocumentSlot;
import com.simonepugliese.taxreport.core.domain.ExpenseEntry;
import com.simonepugliese.taxreport.core.domain.RuleEngine;
import com.simonepugliese.taxreport.core.dto.*;
import com.simonepugliese.taxreport.core.exception.StorageException;
import com.simonepugliese.taxreport.core.exception.ValidationException;
import com.simonepugliese.taxreport.core.spi.MetadataRepository;
import com.simonepugliese.taxreport.core.spi.StorageStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.stream.Collectors;

// Package-private: solo la Factory in questo package può vederla
class ExpenseServiceImpl implements ExpenseService {
    private static final Logger logger = LoggerFactory.getLogger(ExpenseServiceImpl.class);

    private final StorageStrategy storage;
    private final MetadataRepository repository;
    private final RuleEngine ruleEngine;

    ExpenseServiceImpl(StorageStrategy storage, MetadataRepository repository, RuleEngine ruleEngine) {
        this.storage = storage;
        this.repository = repository;
        this.ruleEngine = ruleEngine;
    }

    @Override
    public void initYear(int year) {
        logger.info("Pre-caricamento regole anno fiscale {}", year);
        ruleEngine.loadRules(year);
    }

    @Override
    public String createExpense(NewExpenseDTO dto) {
        logger.info("Creazione spesa: Cat={}, Data={}", dto.categoryId(), dto.dateRaw());

        if (dto.description() == null || dto.description().isBlank()) {
            throw new ValidationException("La descrizione è obbligatoria.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dto.dateRaw());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Formato data non valido (richiesto YYYY-MM-DD).");
        }

        // 1. Sanitizzazione Descrizione (Normalizza accenti e rimuove caratteri speciali)
        String normalized = Normalizer.normalize(dto.description(), Normalizer.Form.NFD);
        String safeDesc = normalized.replaceAll("[^\\p{ASCII}]", "") // Rimuove accenti separati
                .replaceAll("[^a-zA-Z0-9]", "_"); // Sostituisce non alfanumerici con _

        String dateStr = dto.dateRaw().replace("-", ""); // 20241021

        // Base Path: /2024/CODICE/CATEGORIA/YYYYMMDD_Descrizione
        String relativeBasePath = String.format("/%d/%s/%s/%s_%s",
                dto.year(), dto.fiscalCode(), dto.categoryId(), dateStr, safeDesc);

        // 2. Gestione Collisioni
        String finalPath = relativeBasePath;
        int counter = 1;
        while (storage.exists(finalPath)) {
            logger.debug("Path {} esistente, tento suffisso _{}", finalPath, counter);
            finalPath = relativeBasePath + "_" + counter++;
        }

        // 3. Creazione Cartella
        try {
            boolean created = storage.createDirectory(finalPath);
            if (!created) {
                throw new StorageException("Impossibile creare cartella (già esistente?): " + finalPath, null);
            }
        } catch (Exception e) {
            throw new StorageException("Errore I/O creazione cartella: " + finalPath, e);
        }

        ExpenseEntry entry = new ExpenseEntry(UUID.randomUUID(), dto.categoryId(), date, dto.description());
        entry.setPhysicalPath(finalPath);

        // 4. Applicazione Regole (Multi-anno gestito da RuleEngine)
        ruleEngine.applyRules(entry);

        repository.save(entry);

        logger.info("Spesa creata con ID: {}", entry.getId());
        return entry.getId().toString();
    }

    @Override
    public void uploadDocument(String expenseId, DocType type, InputStream content) {
        UUID uid = UUID.fromString(expenseId);
        logger.info("Upload documento {} per spesa {}", type, uid);

        ExpenseEntry entry = repository.findById(uid)
                .orElseThrow(() -> new ValidationException("Spesa non trovata con ID: " + expenseId));

        if (entry.getStatus() == ValidationStatus.LOCKED) {
            throw new ValidationException("Impossibile modificare una spesa in anno chiuso (LOCKED).");
        }

        // 1. Recupero Slot e Nome Previsto (dal JSON)
        var slot = entry.getSlot(type);
        if (slot == null) {
            throw new ValidationException("Documento tipo " + type + " non previsto per questa categoria.");
        }

        // FIX: Usiamo il nome previsto dallo slot, non uno switch hardcoded
        String targetFilename = slot.getExpectedFilename();

        // 2. Salvataggio fisico
        storage.saveFile(entry.getPhysicalPath(), targetFilename, content);

        // 3. Aggiornamento Slot
        slot.fill(targetFilename, 0);

        // 4. Ricalcolo Stato
        updateStatusAndSave(entry, uid);

        logger.info("Upload completato.");
    }

    @Override
    public void deleteDocument(String expenseId, DocType type) {
        UUID uid = UUID.fromString(expenseId);
        ExpenseEntry entry = repository.findById(uid)
                .orElseThrow(() -> new ValidationException("Spesa non trovata."));

        if (entry.getStatus() == ValidationStatus.LOCKED) {
            throw new ValidationException("Impossibile eliminare file in anno chiuso.");
        }

        var slot = entry.getSlot(type);
        if (slot != null && slot.isFilled()) {
            // 1. Cancella Fisico
            String filename = slot.getCurrentFile().filename();
            storage.deleteFile(entry.getPhysicalPath(), filename);

            // 2. Pulisci Logico
            slot.clear();

            // 3. Ricalcola Stato
            updateStatusAndSave(entry, uid);
            logger.info("Documento {} cancellato.", type);
        }
    }

    @Override
    public ExpenseStatusDTO getStatus(String expenseId) {
        UUID uid = UUID.fromString(expenseId);
        ExpenseEntry entry = repository.findById(uid)
                .orElseThrow(() -> new ValidationException("Spesa non trovata."));

        // Mapping manuale verso DTO
        var slotsStatus = entry.getSlots().values().stream()
                .collect(Collectors.toMap(DocumentSlot::getType, DocumentSlot::isFilled));

        var missingDocs = entry.getSlots().values().stream()
                .filter(s -> s.isMandatory() && !s.isFilled())
                .map(s -> "Manca: " + s.getExpectedFilename() + " (" + s.getType() + ")")
                .collect(Collectors.toList());

        return new ExpenseStatusDTO(
                entry.getId().toString(),
                entry.getStatus(),
                entry.getPhysicalPath(),
                slotsStatus,
                missingDocs
        );
    }

    private void updateStatusAndSave(ExpenseEntry entry, UUID uid) {
        ValidationStatus newStatus = ruleEngine.validate(entry);
        entry.setStatus(newStatus);
        repository.updateStatus(uid, newStatus);
        repository.save(entry);
    }
}