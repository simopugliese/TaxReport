package pugliesesimone.taxreport.service;

import pugliesesimone.taxreport.exception.ServiceException;
import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.model.Attachment;
import pugliesesimone.taxreport.model.Document;
import pugliesesimone.taxreport.model.Expense;
import pugliesesimone.taxreport.storage.StorageInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TaxReportService {
    private static final Logger logger = LoggerFactory.getLogger(TaxReportService.class);

    private final StorageInterface storage;
    private final MetadataInterface metadata;

    public TaxReportService(StorageInterface storage, MetadataInterface metadata) {
        this.storage = storage;
        this.metadata = metadata;
    }

    public void registerExpense(Expense expense, List<Attachment> attachments) {
        List<Document> savedDocuments = new ArrayList<>();

        String safeDate = sanitize(expense.getRawDate());
        String safeDesc = sanitize(expense.getDescription());

        String leafFolder = String.format("%s_%s_%s",
                safeDate,
                safeDesc.isEmpty() ? "NoDesc" : safeDesc,
                expense.getId().toString()
        );

        String folderPath = Paths.get(
                expense.getYear(),
                expense.getPerson().getFiscalCode(),
                expense.getExpenseType().name(),
                leafFolder
        ).toString();

        try {
            if (!storage.existsFolder(folderPath)) {
                boolean created = storage.createFolder(folderPath);

                if (!created && !storage.existsFolder(folderPath)) {
                    throw new ServiceException("Impossibile creare cartella: " + folderPath);
                }
            }

            for (Attachment att : attachments) {
                String safeFilename = sanitize(att.getOriginalFilename());

                if (!storage.saveFile(folderPath, safeFilename, att.getContent())) {
                    throw new ServiceException("Errore IO salvataggio file: " + safeFilename);
                }

                String fullPath = Paths.get(folderPath, safeFilename).toString();
                Document doc = new Document(att.getType(), fullPath);

                savedDocuments.add(doc);
                expense.addDocument(doc);
            }

            metadata.save(expense);

            logger.info("Spesa {} salvata in: {}", expense.getId(), folderPath);

        } catch (Exception e) {
            logger.error("Errore salvataggio spesa {}. Eseguo rollback.", expense.getId(), e);

            rollbackFiles(savedDocuments);

            if (expense.getDocuments() != null) {
                expense.getDocuments().removeAll(savedDocuments);
            }

            throw new ServiceException("Salvataggio fallito: " + e.getMessage(), e);
        }
    }

    private void rollbackFiles(List<Document> documents) {
        for (Document doc : documents) {
            try {
                java.nio.file.Path p = java.nio.file.Path.of(doc.getRelativePath());
                String filename = p.getFileName().toString();
                String folder = p.getParent() != null ? p.getParent().toString() : "";

                storage.deleteFile(folder, filename);
            } catch (Exception ex) {
                logger.warn("Rollback parziale fallito per: {}", doc.getRelativePath());
            }
        }
    }

    private String sanitize(String input) {
        if (input == null || input.isBlank()) return "";

        // \p{L} = Qualsiasi lettera Unicode (inclusi à, è, é, ì, ò, ù)
        // \p{N} = Qualsiasi numero
        // \. \- _ = Punto, trattino, underscore (già sicuri)
        // Tutto il resto (spazi, slash, emoji, simboli strani) diventa "_"
        return input.trim().replaceAll("[^\\p{L}\\p{N}\\.\\-_]", "_");
    }
}