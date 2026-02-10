package pugliesesimone.taxreport;

import pugliesesimone.taxreport.model.*;
import pugliesesimone.taxreport.rules.ComplianceResult;
import pugliesesimone.taxreport.rules.ComplianceService;
import pugliesesimone.taxreport.rules.RuleEngine;
import pugliesesimone.taxreport.service.TaxReportService;
import pugliesesimone.taxreport.storage.FileSystemStorage;
import pugliesesimone.taxreport.metadata.SQLiteMetadata;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

public class LocalIntegrationTest {

    private static final String TEST_ROOT = "./test_env";

    public static void main(String[] args) {
        System.out.println("=== AVVIO LOCAL INTEGRATION TEST (FULL CYCLE) ===");

        try {
            new File(TEST_ROOT).mkdirs();
            FileSystemStorage storage = new FileSystemStorage(TEST_ROOT);
            SQLiteMetadata metadata = new SQLiteMetadata(TEST_ROOT);

            createDummyRulesFile();

            Person me = new Person("Simone Engineer", "PGLSMN90A01H501X");
            bootstrapPerson(metadata, me);

            TaxReportService service = new TaxReportService(storage, metadata);
            RuleEngine ruleEngine = new RuleEngine(storage);
            ComplianceService complianceService = new ComplianceService(metadata, ruleEngine);

            System.out.println("\n--- SCENARIO A: Inserimento Spesa COMPLETA ---");
            Expense expOk = new Expense("2024", me, ExpenseType.PAGAMENTO_UNIVERSITARIO, "Rata Completa", "15/01/2024");

            service.registerExpense(expOk, List.of(
                    new Attachment(DocumentType.FATTURA, "fattura.pdf", stream("DATA")),
                    new Attachment(DocumentType.RICEVUTA_PAGAMENTO, "bonifico.pdf", stream("DATA"))
            ));
            System.out.println(">> Spesa OK registrata. Stato attuale: " + expOk.getExpenseState());


            System.out.println("\n--- SCENARIO B: Inserimento Spesa INCOMPLETA ---");
            Expense expKo = new Expense("2024", me, ExpenseType.PAGAMENTO_UNIVERSITARIO, "Rata Mancante", "20/01/2024");

            service.registerExpense(expKo, List.of(
                    new Attachment(DocumentType.FATTURA, "fattura_only.pdf", stream("DATA"))
            ));
            System.out.println(">> Spesa KO registrata. Stato attuale: " + expKo.getExpenseState());

            System.out.println("\n--- SCENARIO C: Esecuzione REPORT & VALIDAZIONE ---");

            List<Expense> reportList = List.of(expOk, expKo);

            complianceService.validateAndUpdateStatus(reportList);

            System.out.println(">> Verifica Stati Finali:");

            Expense dbExpOk = metadata.findById(expOk.getId()).get();
            Expense dbExpKo = metadata.findById(expKo.getId()).get();

            System.out.println("   Spesa A (Completa): " + dbExpOk.getExpenseState());
            if (dbExpOk.getExpenseState() == ExpenseState.COMPLETED) System.out.println("   -> [PASS] Corretto.");
            else System.err.println("   -> [FAIL] Doveva essere COMPLETED!");

            System.out.println("   Spesa B (Incompleta): " + dbExpKo.getExpenseState());
            if (dbExpKo.getExpenseState() == ExpenseState.PARTIAL) {
                System.out.println("   -> [PASS] Corretto.");
                ComplianceResult res = complianceService.checkCompliance(dbExpKo);
                System.out.println("      Mancano: " + res.getMissingDocuments());
            } else {
                System.err.println("   -> [FAIL] Doveva essere PARTIAL!");
            }

            System.out.println("\n=== TEST COMPLETATO ===");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void createDummyRulesFile() throws Exception {
        File configDir = new File(TEST_ROOT, "config");
        configDir.mkdirs();
        File jsonFile = new File(configDir, "rules_2024.json");

        String json = """
        {
          "PAGAMENTO_UNIVERSITARIO": [
            "FATTURA",
            "RICEVUTA_PAGAMENTO"
          ]
        }
        """;

        try (FileWriter fw = new FileWriter(jsonFile)) {
            fw.write(json);
        }
        System.out.println("-> Configurazione Rules creata in: " + jsonFile.getAbsolutePath());
    }

    private static ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static void bootstrapPerson(SQLiteMetadata metadata, Person p) throws Exception {
        File dbFile = new File(TEST_ROOT, "taxreport.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
             PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO persons (id, name, fiscal_code) VALUES (?, ?, ?)")) {
            ps.setString(1, p.getId().toString());
            ps.setString(2, p.getName());
            ps.setString(3, p.getFiscalCode());
            ps.executeUpdate();
        }
    }
}