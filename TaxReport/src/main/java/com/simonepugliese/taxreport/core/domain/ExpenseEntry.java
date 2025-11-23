package com.simonepugliese.taxreport.core.domain;

import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class ExpenseEntry implements Serializable {
    private UUID id;
    private String categoryId;
    private LocalDate date;
    private String sanitizedDescription;

    private String physicalPath;

    private ValidationStatus status = ValidationStatus.EMPTY;

    private final Map<DocType, DocumentSlot> slots = new EnumMap<>(DocType.class);

    public ExpenseEntry() {
    }

    public ExpenseEntry(UUID id, String categoryId, LocalDate date, String sanitizedDescription) {
        this.id = id;
        this.categoryId = categoryId;
        this.date = date;
        this.sanitizedDescription = sanitizedDescription;
    }

    public void addSlot(DocumentSlot slot) {
        this.slots.put(slot.getType(), slot);
    }

    public DocumentSlot getSlot(DocType type) {
        return this.slots.get(type);
    }

    boolean hasSlot(DocType type) {
        return this.slots.containsKey(type);
    }

    public UUID getId() { return id; }
    public String getCategoryId() { return categoryId; }
    public String getPhysicalPath() { return physicalPath; }
    public void setPhysicalPath(String physicalPath) { this.physicalPath = physicalPath; }

    public ValidationStatus getStatus() { return status; }
    public void setStatus(ValidationStatus status) { this.status = status; }

    public Map<DocType, DocumentSlot> getSlots() {
        return Map.copyOf(slots);
    }
}
