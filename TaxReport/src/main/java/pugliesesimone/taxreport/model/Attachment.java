package pugliesesimone.taxreport.model;

import java.io.InputStream;

public class Attachment {
    private final DocumentType type;
    private final String originalFilename;
    private final InputStream content;

    public Attachment(DocumentType type, String originalFilename, InputStream content) {
        this.type = type;
        this.originalFilename = originalFilename;
        this.content = content;
    }

    public DocumentType getType() { return type; }
    public String getOriginalFilename() { return originalFilename; }
    public InputStream getContent() { return content; }
}
