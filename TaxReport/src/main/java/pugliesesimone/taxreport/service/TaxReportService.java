package pugliesesimone.taxreport.service;

import pugliesesimone.taxreport.exception.ServiceException;
import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.model.Attachment;
import pugliesesimone.taxreport.model.Document;
import pugliesesimone.taxreport.model.Expense;
import pugliesesimone.taxreport.storage.StorageInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public void registerExpense(Expense expense, List<Attachment> attachments) {
        List<Document> savedDocuments = new ArrayList<>();

        // FIX 1: Protezione contro perdita dati (Merge dei documenti)
        // Se stiamo aggiornando una spesa esistente, dobbiamo assicurarci di non perdere
        // i documenti già salvati nel DB, poiché metadata.save() fa una replace completa.
        try {
            Optional<Expense> existing = metadata.findById(expense.getId());
            if (existing.isPresent()) {
                for (Document oldDoc : existing.get().getDocuments()) {
                    // Evita duplicati controllando l'ID
                    boolean alreadyPresent = expense.getDocuments().stream()
                            .anyMatch(d -> d.getId().equals(oldDoc.getId()));

                    if (!alreadyPresent) {
                        expense.addDocument(oldDoc);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Impossibile recuperare documenti esistenti per merge ID {}. Procedo comunque.", expense.getId(), e);
        }

        String safeDate = sanitize(expense.getRawDate());
        String safeDesc = sanitize(expense.getDescription());

        String leafFolder = String.format("%s_%s_%s",
                safeDate,
                safeDesc.isEmpty() ? "NoDesc" : safeDesc,
                expense.getId().toString()
        );

        // Usiamo '/' standard, lo storage si occuperà di convertirlo se serve (es. su Windows)
        String folderPath = String.join("/",
                expense.getYear(),
                expense.getPerson().getFiscalCode(),
                expense.getExpenseType().name(),
                leafFolder
        );

        try {
            if (!storage.existsFolder(folderPath)) {
                boolean created = storage.createFolder(folderPath);
                if (!created && !storage.existsFolder(folderPath)) {
                    throw new ServiceException("Impossibile creare cartella: " + folderPath);
                }
            }

            for (Attachment att : attachments) {
                // FIX 2: Collisione Nomi File
                // Aggiungiamo timestamp per garantire unicità (es. "Fattura.pdf" caricato 2 volte non si sovrascrive)
                String sanitizedOriginal = sanitize(att.getOriginalFilename());
                String uniqueFilename = System.currentTimeMillis() + "_" + sanitizedOriginal;

                if (!storage.saveFile(folderPath, uniqueFilename, att.getContent())) {
                    throw new ServiceException("Errore IO salvataggio file: " + uniqueFilename);
                }

                String fullPath = folderPath + "/" + uniqueFilename;
                Document doc = new Document(att.getType(), fullPath);

                savedDocuments.add(doc);
                expense.addDocument(doc);
            }

            metadata.save(expense);

            logger.info("Spesa {} salvata in: {}", expense.getId(), folderPath);

        } catch (Exception e) {
            logger.error("Errore salvataggio spesa {}. Eseguo rollback.", expense.getId(), e);

            rollbackFiles(savedDocuments);

            // Pulizia dell'oggetto in memoria in caso di errore
            if (expense.getDocuments() != null) {
                expense.getDocuments().removeAll(savedDocuments);
            }

            throw new ServiceException("Salvataggio fallito: " + e.getMessage(), e);
        }
    }

    private void rollbackFiles(List<Document> documents) {
        for (Document doc : documents) {
            try {
                // Parsing manuale del path per il rollback per essere indipendenti da Path.of
                String relPath = doc.getRelativePath();
                int lastSep = Math.max(relPath.lastIndexOf('/'), relPath.lastIndexOf('\\'));

                String folder = (lastSep > 0) ? relPath.substring(0, lastSep) : "";
                String filename = (lastSep >= 0 && lastSep < relPath.length()) ? relPath.substring(lastSep + 1) : relPath;

                storage.deleteFile(folder, filename);
            } catch (Exception ex) {
                logger.warn("Rollback parziale fallito per: {}", doc.getRelativePath());
            }
        }
    }

    private String sanitize(String input) {
        if (input == null || input.isBlank()) return "";
        return SAFE_FILENAME_PATTERN.matcher(input.trim()).replaceAll("_");
    }
}