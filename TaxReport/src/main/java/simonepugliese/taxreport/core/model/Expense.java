package simonepugliese.taxreport.core.model;

import java.util.List;
import java.util.UUID;

public class Expense {
    private UUID id;
    private String year;
    private ExpenseType expenseType;
    private Person person;
    List<Document> documents;
}
