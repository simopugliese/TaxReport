package com.simonepugliese.taxreport.core.api;

import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.ExpenseStatusDTO;
import com.simonepugliese.taxreport.core.dto.NewExpenseDTO;
import java.io.InputStream;

public interface ExpenseService {

    /**
     * Inizializza il motore caricando le regole per l'anno specificato.
     * Da chiamare all'avvio dell'app.
     */
    void initYear(int year);

    /**
     * Crea una nuova voce di spesa (Entry).
     * @return L'ID univoco (UUID) della spesa creata.
     */
    String createExpense(NewExpenseDTO dto);

    /**
     * Carica un documento nello slot specifico.
     * @param expenseId L'ID della spesa.
     * @param type Il tipo di documento (es. INVOICE).
     * @param content Lo stream del file (verrà chiuso dal metodo).
     */
    void uploadDocument(String expenseId, DocType type, InputStream content);

    /**
     * Rimuove un documento caricato e resetta lo slot.
     * Ricalcola lo stato della spesa (es. da COMPLIANT a PARTIAL).
     */
    void deleteDocument(String expenseId, DocType type);

    /**
     * Recupera lo stato aggiornato della spesa.
     * Utile per aggiornare la GUI dopo un upload.
     */
    ExpenseStatusDTO getStatus(String expenseId);
}