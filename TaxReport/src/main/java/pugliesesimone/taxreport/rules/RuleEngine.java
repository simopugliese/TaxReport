package pugliesesimone.taxreport.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pugliesesimone.taxreport.model.DocumentType;
import pugliesesimone.taxreport.model.ExpenseType;
import pugliesesimone.taxreport.storage.StorageInterface;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RuleEngine {
    private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);
    private static final String CONFIG_FOLDER = "config";

    private final StorageInterface storage;
    private final ObjectMapper mapper;

    // Cache: Anno -> Mappa Regole (ExpenseType -> Lista Documenti)
    private final Map<String, Map<ExpenseType, List<DocumentType>>> ruleCache = new ConcurrentHashMap<>();

    public RuleEngine(StorageInterface storage) {
        this.storage = storage;
        this.mapper = new ObjectMapper();
    }

    /**
     * Ritorna la lista dei documenti obbligatori per un dato anno e tipo di spesa.
     * Se non trova il file di regole per l'anno, assume che non ci siano obblighi (lista vuota).
     */
    public List<DocumentType> getMandatoryDocuments(String year, ExpenseType type) {
        Map<ExpenseType, List<DocumentType>> rulesForYear = ruleCache.computeIfAbsent(year, this::loadRulesFromStorage);
        return rulesForYear.getOrDefault(type, Collections.emptyList());
    }

    /**
     * Carica il file rules_{YEAR}.json dallo storage (es. config/rules_2024.json)
     */
    private Map<ExpenseType, List<DocumentType>> loadRulesFromStorage(String year) {
        String filename = "rules_" + year + ".json";

        // Verifica esistenza cartella config (creala se non esiste, per comodità)
        if (!storage.existsFolder(CONFIG_FOLDER)) {
            try {
                storage.createFolder(CONFIG_FOLDER);
            } catch (Exception e) {
                logger.warn("Impossibile creare cartella config", e);
            }
        }

        try (InputStream is = storage.loadFile(CONFIG_FOLDER, filename)) {
            // Parsing JSON: { "VISITA_MEDICA": ["FATTURA"], ... }
            return mapper.readValue(is, new TypeReference<Map<ExpenseType, List<DocumentType>>>() {});
        } catch (Exception e) {
            logger.warn("Nessun file regole trovato per l'anno {} (cercato: {}/{}), oppure errore di parsing. Nessuna regola applicata.",
                    year, CONFIG_FOLDER, filename);
            // Ritorna mappa vuota: nessuna regola = tutto lecito
            return Collections.emptyMap();
        }
    }

    /**
     * Forza lo svuotamento della cache (utile se modifichi il JSON a runtime)
     */
    public void reloadRules() {
        ruleCache.clear();
    }
}