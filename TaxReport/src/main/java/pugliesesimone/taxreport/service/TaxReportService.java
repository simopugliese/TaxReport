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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
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

    public MetadataInterface getMetadata() {
        return metadata;
    }

    public StorageInterface getStorage() {
        return storage;
    }

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

    public String runComplianceCheck(String year) {
        try {
            logger.info("Avvio Compliance Check Scalabile (Batch + Throttling) per anno {}", year);

            RuleEngine ruleEngine = new RuleEngine(storage);
            ComplianceService complianceService = new ComplianceService(metadata, ruleEngine);

            int pageSize = 100;
            int offset = 0;
            long totalProcessed = 0;

            Semaphore dbPermits = new Semaphore(10);

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                while (true) {
                    List<Expense> page = metadata.findByYear(year, pageSize, offset);
                    if (page.isEmpty()) break;

                    final List<Expense> currentBatch = page;
                    final int currentOffset = offset;
                    totalProcessed += currentBatch.size();

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            dbPermits.acquire();
                            try {
                                complianceService.validateAndUpdateStatus(currentBatch);
                            } finally {
                                dbPermits.release();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (Exception e) {
                            logger.error("Errore elaborazione batch offset {}", currentOffset, e);
                        }
                    }, executor);

                    futures.add(future);
                    offset += pageSize;
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            return String.format("""
                Report Anno %s Generato con Successo.
                -------------------------------------
                Totale Spese Processate: %d
                Modalità: Scalable Batch Processing
                """, year, totalProcessed);

        } catch (Exception e) {
            logger.error("Errore durante compliance check anno {}", year, e);
            throw new ServiceException("Errore generazione report: " + e.getMessage(), e);
        }
    }

    /*TODO: gemini mi ha segnalato un caso limite. Se il server crasha esattamente
    tra il metadata.save(expense) e la validazione, ti ritrovi una spesa salvata
    ma con stato di compliance non calcolato (o vecchio). Questo non è un problema
    se ricalcoli il report, però andrebbe sistemato
    */
    public void registerExpense(Expense expense, List<Attachment> attachments) {
        List<Document> savedDocuments = new ArrayList<>();

        try {
            Optional<Expense> existingOpt = metadata.findById(expense.getId());
            if (existingOpt.isPresent()) {
                Expense existing = existingOpt.get();
                List<Document> toRemove = existing.getDocuments().stream()
                        .filter(oldDoc -> expense.getDocuments().stream()
                                .noneMatch(newDoc -> newDoc.getId().equals(oldDoc.getId())))
                        .toList();

                for (Document doc : toRemove) {
                    try {
                        File f = new File(doc.getRelativePath());
                        String folder = f.getParent() == null ? "" : f.getParent();
                        storage.deleteFile(folder, f.getName());
                    } catch (Exception ex) {
                        logger.warn("Impossibile eliminare file orfano: {}", doc.getRelativePath());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Errore pulizia pre-save: {}", e.getMessage());
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

            savedDocuments.forEach(expense::addDocument);
            metadata.save(expense);
            logger.info("Spesa {} salvata in: {}", expense.getId(), folderPath);

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
