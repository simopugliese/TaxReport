package pugliesesimone.taxreport.service;

import pugliesesimone.taxreport.exception.ServiceException;
import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.model.*;
import pugliesesimone.taxreport.rules.ComplianceService;
import pugliesesimone.taxreport.rules.RuleEngine;
import pugliesesimone.taxreport.storage.StorageInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    // --- Metodi Report & Compliance ---

    /**
     * Esegue il controllo di conformità su tutte le spese di un dato anno.
     * Aggiorna lo stato nel DB e restituisce un riepilogo testuale.
     */
    public String runComplianceCheck(String year) {
        try {
            // 1. Setup Motore Regole (on-the-fly)
            RuleEngine ruleEngine = new RuleEngine(storage);
            ComplianceService complianceService = new ComplianceService(metadata, ruleEngine);

            // 2. Recupera Spese
            List<Expense> expenses = metadata.findByYear(year);
            if (expenses.isEmpty()) {
                return "Nessuna spesa trovata per l'anno " + year;
            }

            // 3. Esegui Validazione (aggiorna gli stati nel DB)
            complianceService.validateAndUpdateStatus(expenses);

            // 4. Calcola Statistiche
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

        // Merge documenti esistenti
        try {
            Optional<Expense> existing = metadata.findById(expense.getId());
            if (existing.isPresent()) {
                for (Document oldDoc : existing.get().getDocuments()) {
                    boolean alreadyPresent = expense.getDocuments().stream()
                            .anyMatch(d -> d.getId().equals(oldDoc.getId()));
                    if (!alreadyPresent) expense.addDocument(oldDoc);
                }
            }
        } catch (Exception e) {
            logger.warn("Merge fallito per ID {}. Procedo.", expense.getId(), e);
        }

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
                int lastSep = Math.max(relPath.lastIndexOf('/'), relPath.lastIndexOf('\\'));
                String folder = (lastSep > 0) ? relPath.substring(0, lastSep) : "";
                String filename = (lastSep >= 0 && lastSep < relPath.length()) ? relPath.substring(lastSep + 1) : relPath;
                storage.deleteFile(folder, filename);
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