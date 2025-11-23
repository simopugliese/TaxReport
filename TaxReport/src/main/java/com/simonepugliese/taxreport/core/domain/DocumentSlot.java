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

    // Costruttore vuoto per Jackson
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

    // Ora è public per permettere la cancellazione dal Service
    public void clear() {
        this.currentFile = null;
    }

    // Getters standard
    public DocType getType() { return type; }
    public boolean isMandatory() { return mandatory; }
    public String getExpectedFilename() { return expectedFilename; }
    public FileMetadata getCurrentFile() { return currentFile; }

    // Record interno per i metadati del file
    public record FileMetadata(String filename, long size, LocalDateTime uploadedAt) implements Serializable {}
}