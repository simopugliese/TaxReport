package com.simonepugliese.taxreport.core.domain;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.simonepugliese.taxreport.core.dto.DocType;
import java.io.Serializable;
import java.time.LocalDateTime;

public class DocumentSlot implements Serializable {
    private DocType type;
    private boolean mandatory;
    private String expectedFilename; // Es. "Fattura.pdf"

    private FileMetadata currentFile; // Null se vuoto

    public DocumentSlot() {
    }

    public DocumentSlot(DocType type, boolean mandatory, String expectedFilename) {
        this.type = type;
        this.mandatory = mandatory;
        this.expectedFilename = expectedFilename;
    }

    @JsonIgnore
    public boolean isFilled() {
        return currentFile != null;
    }

    public void fill(String actualFilename, long size) {
        this.currentFile = new FileMetadata(actualFilename, size, LocalDateTime.now());
    }

    void clear() {
        this.currentFile = null;
    }

    // Getters standard
    public DocType getType() { return type; }
    public boolean isMandatory() { return mandatory; }
    public String getExpectedFilename() { return expectedFilename; }
    FileMetadata getCurrentFile() { return currentFile; }

    // Record interno per i metadati del file
    record FileMetadata(String filename, long size, LocalDateTime uploadedAt) implements Serializable {}
}
