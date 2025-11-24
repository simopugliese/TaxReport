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

    public ComplianceResult checkCompliance(Expense expense) {
        // 1. Chiedi al RuleEngine cosa serve per questo Anno e Tipo
        List<DocumentType> requiredTypes = ruleEngine.getMandatoryDocuments(expense.getYear(), expense.getExpenseType());

        if (requiredTypes.isEmpty()) {
            return new ComplianceResult(true, List.of());
        }

        // 2. Cosa abbiamo caricato?
        Set<DocumentType> presentTypes = expense.getDocuments().stream()
                .map(Document::getDocumentType)
                .collect(Collectors.toSet());

        // 3. Calcola Delta
        List<DocumentType> missing = new ArrayList<>();
        for (DocumentType req : requiredTypes) {
            if (!presentTypes.contains(req)) {
                missing.add(req);
            }
        }

        return new ComplianceResult(missing.isEmpty(), missing);
    }

    /**
     * Scansiona una lista di spese, verifica la compliance e aggiorna lo stato su DB
     * (es. passa da PARTIAL a COMPLETED).
     */
    public void validateAndUpdateStatus(List<Expense> expenses) {
        for (Expense exp : expenses) {
            // Se è BLOCKED (blocco manuale utente), non tocchiamo nulla
            if (exp.getExpenseState() == ExpenseState.BLOCKED) continue;

            ComplianceResult result = checkCompliance(exp);

            // Logica di transizione stato
            ExpenseState newState = result.isCompliant() ? ExpenseState.COMPLETED : ExpenseState.PARTIAL;

            // Aggiorniamo solo se lo stato cambia per evitare write inutili su DB
            if (newState != exp.getExpenseState()) {
                exp.setExpenseState(newState);
                try {
                    metadata.save(exp);
                    logger.info("Stato spesa {} aggiornato a {}", exp.getId(), newState);
                } catch (Exception e) {
                    logger.error("Errore salvataggio stato spesa {}", exp.getId(), e);
                }
            }
        }
    }
}