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

    public void validateAndUpdateStatus(List<Expense> expenses) {
        List<Expense> toUpdate = new ArrayList<>();

        for (Expense exp : expenses) {
            if (exp.getExpenseState() == ExpenseState.BLOCKED) continue;

            ComplianceResult result = checkCompliance(exp);

            ExpenseState newState = result.isCompliant() ? ExpenseState.COMPLETED : ExpenseState.PARTIAL;

            if (newState != exp.getExpenseState()) {
                exp.setExpenseState(newState);
                toUpdate.add(exp);
            }
        }

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