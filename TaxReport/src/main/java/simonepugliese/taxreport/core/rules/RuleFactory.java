package simonepugliese.taxreport.core.rules;

import simonepugliese.taxreport.core.model.Expense;
import simonepugliese.taxreport.core.model.ExpenseType;

import java.util.ServiceLoader;

public class RuleFactory {
    private ServiceLoader<ComplianceStrategy> loader;

    public RuleFactory(ServiceLoader<ComplianceStrategy> loader) {
        this.loader = loader;
    }

    public ComplianceStrategy getComplianceStrategy(Expense expense, ExpenseType expenseType) {
        return null;//todo
    }

    public void reloadStrategies() {
     //todo
    }
}
