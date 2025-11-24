package com.simonepugliese.taxreport;

import com.simonepugliese.taxreport.core.api.ExpenseService;
import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.ExpenseStatusDTO;
import com.simonepugliese.taxreport.core.dto.NewExpenseDTO;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import com.simonepugliese.taxreport.core.impl.TaxReportFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Main {
    private static final String DATA_FOLDER = "C:/Temp/TaxReport_Userdata";

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("   TAX REPORT APP - CLI DEMO (Persistent)");
        System.out.println("==========================================");

        System.out.println("[INIT] Avvio servizio su cartella: " + DATA_FOLDER);
        ExpenseService service = TaxReportFactory.createLocalService(DATA_FOLDER);

        try {
            service.initYear(2024);
            System.out.println("[OK] Regole 2024 caricate correttamente.");

            System.out.println("\n[ACTION] Creazione nuova spesa...");
            NewExpenseDTO dto = new NewExpenseDTO(
                    2024,
                    "RSSMRA80A01H501U", // Codice Fiscale
                    "spese_mediche",    // ID Categoria (deve esistere nel rules_2024.json)
                    "2024-11-23",       // Data
                    "Dentista Impianto" // Descrizione
            );

            String expenseId = service.createExpense(dto);
            System.out.println("[OK] Spesa creata. ID: " + expenseId);
            printStatus(service.getStatus(expenseId));

            System.out.println("\n[ACTION] Upload FATTURA in corso...");
            String fakePdfContent = "%PDF-1.5 ... contenuto binario finto della fattura ...";
            InputStream fatStream = new ByteArrayInputStream(fakePdfContent.getBytes(StandardCharsets.UTF_8));

            service.uploadDocument(expenseId, DocType.INVOICE, fatStream);
            System.out.println("[OK] Fattura caricata.");
            printStatus(service.getStatus(expenseId));

            System.out.println("\n[ACTION] Upload PAGAMENTO in corso...");
            InputStream pagStream = new ByteArrayInputStream("Altro contenuto PDF finto per il bonifico".getBytes(StandardCharsets.UTF_8));

            service.uploadDocument(expenseId, DocType.PAYMENT, pagStream);
            System.out.println("[OK] Pagamento caricato.");

            ExpenseStatusDTO finalStatus = service.getStatus(expenseId);
            printStatus(finalStatus);

            if (finalStatus.status() == ValidationStatus.COMPLIANT) {
                System.out.println("\n>>> SUCCESSO! La spesa è CONFORME e pronta per il commercialista. <<<");
            } else {
                System.out.println("\n>>> ATTENZIONE: La spesa è ancora incompleta. <<<");
            }

        } catch (Exception e) {
            System.err.println("\n!!! ERRORE DURANTE L'ESECUZIONE !!!");
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printStatus(ExpenseStatusDTO status) {
        System.out.println("   ------------------------------------------------");
        System.out.println("   | STATO: " + status.status());
        System.out.println("   | Path:  " + status.physicalPath());

        if (status.missingMandatoryDocs().isEmpty()) {
            System.out.println("   | INFO:  Tutti i documenti obbligatori sono presenti.");
        } else {
            System.out.println("   | MANCANO: " + status.missingMandatoryDocs());
        }
        System.out.println("   ------------------------------------------------");
    }
}