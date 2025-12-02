package pugliesesimone.taxreport.service;

import pugliesesimone.taxreport.exception.ServiceException;
import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.model.*;
import pugliesesimone.taxreport.rules.ComplianceService;
import pugliesesimone.taxreport.rules.RuleEngine;
import pugliesesimone.taxreport.storage.StorageInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class TaxReportService {
    private static final Logger logger = LoggerFactory.getLogger(TaxReportService.class);
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\.\\-_]");

    private final StorageInterface storage;
    private final MetadataInterface metadata;

    public TaxReportService(StorageInterface storage, MetadataInterface metadata) {
        this.storage = storage;
        this.metadata = metadata;
    }

    // --- Metodi Anagrafica ---
    public void registerPerson(Person person) {
        try {
            metadata.savePerson(person);
        } catch (Exception e) {
            logger.error("Errore salvataggio anagrafica {}", person.getName(), e);
            throw new ServiceException("Impossibile salvare anagrafica", e);
        }
    }

    public List<Person> getAllPersons() {
        return metadata.findAllPersons();
    }

    // --- Metodi Report & Compliance ---
    public String runComplianceCheck(String year) {
        try {
            RuleEngine ruleEngine = new RuleEngine(storage);
            ComplianceService complianceService = new ComplianceService(metadata, ruleEngine);

            List<Expense> expenses = metadata.findByYear(year);
            if (expenses.isEmpty()) {
                return "Nessuna spesa trovata per l'anno " + year;
            }

            complianceService.validateAndUpdateStatus(expenses);

            long completed = expenses.stream().filter(e -> e.getExpenseState() == ExpenseState.COMPLETED).count();
            long partial = expenses.stream().filter(e -> e.getExpenseState() == ExpenseState.PARTIAL).count();
            long initial = expenses.stream().filter(e -> e.getExpenseState() == ExpenseState.INITIAL).count();

            return String.format("""
                Report Anno %s Generato con Successo.
                -------------------------------------
                Totale Spese: %d
                ✅ COMPLETED (Ok): %d
                ⚠️ PARTIAL (Incomplete): %d
                🆕 INITIAL (Da verificare): %d
                """, year, expenses.size(), completed, partial, initial);

        } catch (Exception e) {
            logger.error("Errore durante compliance check anno {}", year, e);
            throw new ServiceException("Errore generazione report: " + e.getMessage(), e);
        }
    }

    // --- Metodi Core Spesa ---
    public void registerExpense(Expense expense, List<Attachment> attachments) {
        List<Document> savedDocuments = new ArrayList<>();

        // 1. GESTIONE CANCELLAZIONE FILE (Fix Bug File Fantasma)
        try {
            Optional<Expense> existingOpt = metadata.findById(expense.getId());
            if (existingOpt.isPresent()) {
                Expense existing = existingOpt.get();

                // Troviamo i documenti che erano nel DB ma NON sono più nell'oggetto expense passato dalla GUI
                List<Document> toRemove = existing.getDocuments().stream()
                        .filter(oldDoc -> expense.getDocuments().stream()
                                .noneMatch(newDoc -> newDoc.getId().equals(oldDoc.getId())))
                        .toList();

                // Cancelliamo fisicamente i file rimossi
                for (Document doc : toRemove) {
                    File f = new File(doc.getRelativePath());
                    String folder = f.getParent();
                    if (folder == null) folder = "";
                    // Se siamo su SMB/Windows assicuriamoci che i path separator siano gestiti
                    // Ma File(path).getParent() dovrebbe gestire bene

                    storage.deleteFile(folder, f.getName());
                    logger.info("File eliminato fisicamente: {}", doc.getRelativePath());
                }
            }
        } catch (Exception e) {
            logger.warn("Errore durante pulizia vecchi file: {}", e.getMessage());
        }

        // 2. SALVATAGGIO NUOVI FILE
        String safeDate = sanitize(expense.getRawDate());
        String safeDesc = sanitize(expense.getDescription());
        String leafFolder = String.format("%s_%s_%s", safeDate, safeDesc.isEmpty() ? "NoDesc" : safeDesc, expense.getId());
        String folderPath = String.join("/", expense.getYear(), expense.getPerson().getFiscalCode(), expense.getExpenseType().name(), leafFolder);

        try {
            if (!storage.existsFolder(folderPath)) {
                if (!storage.createFolder(folderPath) && !storage.existsFolder(folderPath)) {
                    throw new ServiceException("Impossibile creare cartella: " + folderPath);
                }
            }

            for (Attachment att : attachments) {
                String uniqueFilename = System.currentTimeMillis() + "_" + sanitize(att.getOriginalFilename());
                if (!storage.saveFile(folderPath, uniqueFilename, att.getContent())) {
                    throw new ServiceException("Errore IO salvataggio file: " + uniqueFilename);
                }
                String fullPath = folderPath + "/" + uniqueFilename;
                savedDocuments.add(new Document(att.getType(), fullPath));
            }

            // Aggiungi i nuovi documenti all'oggetto spesa
            savedDocuments.forEach(expense::addDocument);

            metadata.save(expense);
            logger.info("Spesa {} salvata in: {}", expense.getId(), folderPath);

            // 3. FIX: ESEGUI SUBITO IL COMPLIANCE CHECK
            try {
                RuleEngine ruleEngine = new RuleEngine(storage);
                ComplianceService compliance = new ComplianceService(metadata, ruleEngine);
                compliance.validateAndUpdateStatus(List.of(expense));
            } catch (Exception e) {
                logger.error("Impossibile validare la spesa dopo il salvataggio", e);
            }

        } catch (Exception e) {
            logger.error("Rollback per spesa {}", expense.getId(), e);
            rollbackFiles(savedDocuments);
            throw new ServiceException("Salvataggio fallito: " + e.getMessage(), e);
        }
    }

    private void rollbackFiles(List<Document> documents) {
        for (Document doc : documents) {
            try {
                String relPath = doc.getRelativePath();
                File f = new File(relPath);
                storage.deleteFile(f.getParent(), f.getName());
            } catch (Exception ex) {
                logger.warn("Rollback parziale fallito: {}", doc.getRelativePath());
            }
        }
    }

    private String sanitize(String input) {
        if (input == null || input.isBlank()) return "";
        return SAFE_FILENAME_PATTERN.matcher(input.trim()).replaceAll("_");
    }
}