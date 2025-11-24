package simonepugliese.taxreport.core.api;

import simonepugliese.taxreport.core.model.Document;
import simonepugliese.taxreport.core.model.DocumentType;
import simonepugliese.taxreport.core.model.Expense;
import simonepugliese.taxreport.core.model.ExpenseType;
import simonepugliese.taxreport.core.rules.RuleFactory;

import java.util.List;
import java.util.UUID;

public class TaxReportFacade {
    private RuleFactory ruleFactory;
    private ExpenseRepository expenseRepository;

    public TaxReportFacade() {
    }

    public UUID createExpense(String year, String fiscalCode, ExpenseType expenseType){
        return null; //todo
    }

    public void uploadDocument(UUID expenseId, DocumentType documentType, String fileName){
        //todo
    }

    public boolean validateExpense(UUID expenseId){
        return true; //todo
    }

    public List<DocumentType> getMissingDocuments(UUID expenseId){
        return null; //todo
    }
}

