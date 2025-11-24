package com.taxreport.api.dto;

import com.taxreport.api.dto.DocType;
import com.taxreport.api.dto.ValidationStatus;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record ExpenseStatusDTO(
        String expenseId,                  // UUID in formato stringa
        ValidationStatus status,           // Il colore del semaforo
        String physicalPath,               // Dove si trova sul disco (per info)
        Map<DocType, Boolean> slotsFilled, // Es: { INVOICE: true, PAYMENT: false }
        List<String> missingMandatoryDocs  // Es: ["Serve Bonifico Parlante"]
) implements Serializable {}
