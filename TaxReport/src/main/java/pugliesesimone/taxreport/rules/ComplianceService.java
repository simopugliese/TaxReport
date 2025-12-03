package pugliesesimone.taxreport.rules;

import pugliesesimone.taxreport.metadata.MetadataInterface;
import pugliesesimone.taxreport.model.Document;
import pugliesesimone.taxreport.model.DocumentType;
import pugliesesimone.taxreport.model.Expense;
import pugliesesimone.taxreport.model.ExpenseState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ComplianceService {
    private static final Logger logger = LoggerFactory.getLogger(ComplianceService.class);

    private final MetadataInterface metadata;
    private final RuleEngine ruleEngine;

    public ComplianceService(MetadataInterface metadata, RuleEngine ruleEngine) {
        this.metadata = metadata;
        this.ruleEngine = ruleEngine;
    }

    /**
     * Verifica una singola spesa (sola lettura).
     */
    public ComplianceResult checkCompliance(Expense expense) {
        List<DocumentType> requiredTypes = ruleEngine.getMandatoryDocuments(expense.getYear(), expense.getExpenseType());

        if (requiredTypes.isEmpty()) {
            return new ComplianceResult(true, List.of());
        }

        Set<DocumentType> presentTypes = expense.getDocuments().stream()
                .map(Document::getDocumentType)
                .collect(Collectors.toSet());

        List<DocumentType> missing = new ArrayList<>();
        for (DocumentType req : requiredTypes) {
            if (!presentTypes.contains(req)) {
                missing.add(req);
            }
        }

        return new ComplianceResult(missing.isEmpty(), missing);
    }

    /**
     * ESEGUE IL REPORT E AGGIORNA IL DB IN BATCH.
     * Itera sulla lista, calcola il nuovo stato e salva in blocco alla fine.
     */
    public void validateAndUpdateStatus(List<Expense> expenses) {
        List<Expense> toUpdate = new ArrayList<>();

        for (Expense exp : expenses) {
            // Se l'utente ha forzato BLOCKED, non lo tocchiamo automaticamente
            if (exp.getExpenseState() == ExpenseState.BLOCKED) continue;

            ComplianceResult result = checkCompliance(exp);

            // Logica di transizione stato:
            // COMPLIANT -> COMPLETED
            // NON COMPLIANT -> PARTIAL
            ExpenseState newState = result.isCompliant() ? ExpenseState.COMPLETED : ExpenseState.PARTIAL;

            // Scriviamo su DB solo se lo stato cambia davvero
            if (newState != exp.getExpenseState()) {
                exp.setExpenseState(newState);
                toUpdate.add(exp);
            }
        }

        // SALVATAGGIO BATCH (Unica transazione efficiente)
        if (!toUpdate.isEmpty()) {
            logger.info("Salvataggio batch di {} spese aggiornate.", toUpdate.size());
            try {
                metadata.saveAll(toUpdate);
            } catch (Exception e) {
                logger.error("Errore critico salvataggio batch stati", e);
            }
        }
    }
}