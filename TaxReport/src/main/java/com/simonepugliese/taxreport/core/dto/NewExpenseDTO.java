package com.simonepugliese.taxreport.core.dto;

import java.io.Serializable;

public record NewExpenseDTO(
        int year,               // Es. 2024
        String fiscalCode,      // Es. "RSSMRA..."
        String categoryId,      // Es. "spese_mediche" (chiave del JSON regole)
        String dateRaw,         // Formato YYYY-MM-DD
        String description      // Es. "Dentista Dr. Rossi"
) implements Serializable {}
