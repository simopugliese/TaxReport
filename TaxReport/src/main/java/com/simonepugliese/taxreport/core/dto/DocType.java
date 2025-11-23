package com.simonepugliese.taxreport.core.dto;

public enum DocType {
    INVOICE,        // Fattura
    RECEIPT,        // Scontrino / Ricevuta
    PAYMENT,        // Bonifico / Ricevuta Pos
    PRESCRIPTION,   // Ricetta Rossa / Elettronica
    REPORT          // Referto (se serve)
}
