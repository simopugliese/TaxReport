package simonepugliese.taxreport.plugins.year;

import simonepugliese.taxreport.core.model.DocumentType;
import simonepugliese.taxreport.core.model.Expense;
import simonepugliese.taxreport.core.model.ExpenseType;
import simonepugliese.taxreport.core.rules.ComplianceStrategy;

import java.util.List;

public class Strategy2024_Medical implements ComplianceStrategy {
    @Override
    public boolean supports(String year, ExpenseType expenseType) {
        return false;
    }

    @Override
    public boolean isValid(Expense expense) {
        return false;
    }

    @Override
    public List<DocumentType> getMissingTypes(Expense expense) {
        return List.of();
    }
}
