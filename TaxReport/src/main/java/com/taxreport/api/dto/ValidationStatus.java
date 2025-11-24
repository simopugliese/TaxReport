package com.taxreport.api.dto;

public enum ValidationStatus {
    EMPTY,      // Appena creata, cartella vuota
    PARTIAL,    // Manca qualcosa (es. c'è fattura ma no bonifico)
    COMPLIANT,  // Tutto ok (Semaforo Verde)
    LOCKED      // Anno chiuso, non modificabile
}
