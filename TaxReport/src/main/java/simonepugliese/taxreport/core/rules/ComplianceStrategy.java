package simonepugliese.taxreport.core.rules;

import simonepugliese.taxreport.core.model.DocumentType;
import simonepugliese.taxreport.core.model.Expense;
import simonepugliese.taxreport.core.model.ExpenseType;

import java.util.List;

public interface ComplianceStrategy {
    public boolean supports(String year, ExpenseType expenseType);
    public boolean isValid(Expense expense);
    public List<DocumentType> getMissingTypes(Expense expense);
}
