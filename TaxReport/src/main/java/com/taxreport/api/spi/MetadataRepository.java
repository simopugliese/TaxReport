package com.taxreport.api.spi;

import com.simonepugliese.taxreport.core.domain.ExpenseEntry;
import com.taxreport.api.dto.ValidationStatus;

import java.util.Optional;
import java.util.UUID;

public interface MetadataRepository {

    /**
     * Salva o aggiorna l'intera entry.
     * @param entry L'oggetto di dominio da persistere.
     */
    void save(ExpenseEntry entry);

    /**
     * Cerca una entry per ID.
     * @return Un Optional pieno se trovata, vuoto altrimenti.
     */
    Optional<ExpenseEntry> findById(UUID id);

    /**
     * Aggiorna solo lo stato di validazione (ottimizzazione).
     * Utile se non vogliamo risalvare tutto l'oggetto pesante.
     */
    void updateStatus(UUID id, ValidationStatus status);
}
