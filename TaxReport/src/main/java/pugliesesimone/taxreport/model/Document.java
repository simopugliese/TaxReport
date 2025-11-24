package pugliesesimone.taxreport.model;

import java.util.UUID;

public class Document {
    private final UUID id;
    private final DocumentType documentType;
    private String relativePath;

    public Document(DocumentType documentType, String relativePath) {
        this.id = UUID.randomUUID();
        this.documentType = documentType;
        this.relativePath = relativePath;
    }

    public Document(UUID id, DocumentType documentType, String relativePath) {
        this.id = id;
        this.documentType = documentType;
        this.relativePath = relativePath;
    }

    public UUID getId() {
        return id;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }
}
