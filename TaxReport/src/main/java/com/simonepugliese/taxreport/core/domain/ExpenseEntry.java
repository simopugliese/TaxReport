package com.simonepugliese.taxreport.core.domain;

import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

// Visibilità: package-private
public class ExpenseEntry implements Serializable {
    private final UUID id;
    private final String categoryId;
    private final LocalDate date;
    private final String sanitizedDescription;

    // Path relativo alla root dello storage (es. "/2024/RSS.../Dentista/")
    private String physicalPath;

    private ValidationStatus status = ValidationStatus.EMPTY;

    // EnumMap è molto più efficiente e memory-safe per chiavi Enum
    private final Map<DocType, DocumentSlot> slots = new EnumMap<>(DocType.class);

    public ExpenseEntry(UUID id, String categoryId, LocalDate date, String sanitizedDescription) {
        this.id = id;
        this.categoryId = categoryId;
        this.date = date;
        this.sanitizedDescription = sanitizedDescription;
    }

    // --- Logica di Dominio ---

    void addSlot(DocumentSlot slot) {
        this.slots.put(slot.getType(), slot);
    }

    public DocumentSlot getSlot(DocType type) {
        return this.slots.get(type);
    }

    boolean hasSlot(DocType type) {
        return this.slots.containsKey(type);
    }

    // --- Getters & Setters (controllati) ---

    public UUID getId() { return id; }
    public String getCategoryId() { return categoryId; }
    public String getPhysicalPath() { return physicalPath; }
    public void setPhysicalPath(String physicalPath) { this.physicalPath = physicalPath; }

    public ValidationStatus getStatus() { return status; }
    public void setStatus(ValidationStatus status) { this.status = status; }

    // Ritorna una copia non modificabile per evitare che qualcuno manometta gli slot da fuori
    public Map<DocType, DocumentSlot> getSlots() {
        return Map.copyOf(slots);
    }
}
