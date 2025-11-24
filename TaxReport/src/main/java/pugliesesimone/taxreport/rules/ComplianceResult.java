package pugliesesimone.taxreport.rules;

import pugliesesimone.taxreport.model.DocumentType;

import java.util.Collections;
import java.util.List;

public class ComplianceResult {
    private final boolean compliant;
    private final List<DocumentType> missingDocuments;

    public ComplianceResult(boolean compliant, List<DocumentType> missingDocuments) {
        this.compliant = compliant;
        this.missingDocuments = missingDocuments != null ? missingDocuments : Collections.emptyList();
    }

    public boolean isCompliant() {
        return compliant;
    }

    public List<DocumentType> getMissingDocuments() {
        return missingDocuments;
    }

    @Override
    public String toString() {
        if (compliant) return "COMPLIANT";
        return "NON COMPLIANT - Mancano: " + missingDocuments;
    }
}