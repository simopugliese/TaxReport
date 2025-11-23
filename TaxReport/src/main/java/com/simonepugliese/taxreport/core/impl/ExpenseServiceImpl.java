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
        logger.info("Inizializzazione anno fiscale {}", year);
        ruleEngine.loadRules(year);
    }

    @Override
    public String createExpense(NewExpenseDTO dto) {
        logger.info("Creazione spesa: Cat={}, Data={}", dto.categoryId(), dto.dateRaw());

        // 1. Validazione base
        if (dto.description() == null || dto.description().isBlank()) {
            throw new ValidationException("La descrizione è obbligatoria.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dto.dateRaw());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Formato data non valido (richiesto YYYY-MM-DD).");
        }

        // 2. Calcolo Path Fisico (Naming Convention)
        // Rimuove tutto tranne lettere e numeri per sicurezza filesystem
        String safeDesc = dto.description().replaceAll("[^a-zA-Z0-9]", "_");
        String dateStr = dto.dateRaw().replace("-", ""); // 20241021

        // Base: /2024/CODICE/CATEGORIA/YYYYMMDD_Descrizione
        String relativeBasePath = String.format("/%d/%s/%s/%s_%s",
                dto.year(), dto.fiscalCode(), dto.categoryId(), dateStr, safeDesc);

        // 3. Gestione Collisioni (Loop per trovare suffisso libero)
        String finalPath = relativeBasePath;
        int counter = 1;
        while (storage.exists(finalPath)) {
            logger.debug("Path {} esistente, tento suffisso _{}", finalPath, counter);
            finalPath = relativeBasePath + "_" + counter++;
        }

        // 4. Creazione Cartella Fisica
        try {
            boolean created = storage.createDirectory(finalPath);
            if (!created) {
                // Caso limite: concorrenza estrema
                throw new StorageException("Impossibile creare cartella (già esistente?): " + finalPath, null);
            }
        } catch (Exception e) {
            throw new StorageException("Errore I/O creazione cartella: " + finalPath, e);
        }

        // 5. Creazione Entità Logica
        ExpenseEntry entry = new ExpenseEntry(UUID.randomUUID(), dto.categoryId(), date, dto.description());
        entry.setPhysicalPath(finalPath);

        // 6. Applicazione Regole (creazione slot vuoti)
        ruleEngine.applyRules(entry);

        // 7. Persistenza
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

        // 1. Recupero nome standard (es. "Fattura.pdf")
        // Nota: Qui potremmo chiedere al ruleEngine il nome specifico se configurato
        String standardName = ruleEngine.getStandardFilename(type);

        // 2. Salvataggio fisico
        storage.saveFile(entry.getPhysicalPath(), standardName, content);

        // 3. Aggiornamento Slot
        var slot = entry.getSlot(type);
        if (slot == null) {
            // Se l'utente carica un documento non previsto dalle regole per questa categoria
            // Possiamo decidere se bloccarlo o aggiungerlo dinamicamente. Per ora blocchiamo.
            throw new ValidationException("Documento tipo " + type + " non previsto per questa categoria.");
        }
        // Nota: size 0 per ora, se lo stream non lo supporta. In futuro StorageStrategy può ritornare il size.
        slot.fill(standardName, 0);

        // 4. Ricalcolo Stato e Salvataggio
        ValidationStatus newStatus = ruleEngine.validate(entry);
        entry.setStatus(newStatus);

        repository.updateStatus(uid, newStatus);
        repository.save(entry); // Salvataggio completo per aggiornare lo slot

        logger.info("Upload completato. Nuovo stato: {}", newStatus);
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
}
