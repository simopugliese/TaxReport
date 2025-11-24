package pugliesesimone.taxreport.model;

import java.util.*;
import java.util.stream.Collectors;

public class Expense {
    private final UUID id;
    private final String year;
    private final Person person;
    private final ExpenseType expenseType;
    private String description;
    private String rawDate;
    private ExpenseState expenseState;
    private final Collection<Document> documents = new ArrayList<>();

    public Expense(String year, Person person, ExpenseType expenseType, String description, String rawDate) {
        this.id = UUID.randomUUID();
        this.year = year;
        this.person = person;
        this.expenseType = expenseType;
        this.description = description;
        this.rawDate = rawDate;
        this.expenseState = ExpenseState.INITIAL;
    }

    public Expense(UUID id, String year, Person person, ExpenseType expenseType, String description, String rawDate, ExpenseState state) {
        this.id = id;
        this.year = year;
        this.person = person;
        this.expenseType = expenseType;
        this.description = description;
        this.rawDate = rawDate;
        this.expenseState = state;
    }

    public UUID getId() {
        return id;
    }

    public String getYear() {
        return year;
    }

    public Person getPerson() {
        return person;
    }

    public ExpenseType getExpenseType() {
        return expenseType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRawDate() {
        return rawDate;
    }

    public void setRawDate(String rawDate) {
        this.rawDate = rawDate;
    }

    public ExpenseState getExpenseState() {
        return expenseState;
    }

    public void setExpenseState(ExpenseState expenseState) {
        this.expenseState = expenseState;
    }

    public Collection<Document> getDocuments() {
        return documents;
    }

    public void addDocument(Document document) {
        this.documents.add(document);
    }

    public void setDocuments(Collection<Document> docs) {
        this.documents.clear();
        if (docs != null) {
            this.documents.addAll(docs);
        }
    }

    public Collection<Document> getDocumentByType(DocumentType documentType) {
        return this.documents
                .stream()
                .filter(document -> document.getDocumentType().equals(documentType))
                .collect(Collectors.toList());
    }

    public boolean removeDocumentById(UUID id){
        Document toBeRemoved = this.documents
                .stream()
                .filter(document -> document.getId().equals(id))
                .toList().getFirst();

        return this.documents.remove(toBeRemoved);
    }
}
