package pugliesesimone.taxreport.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pugliesesimone.taxreport.exception.ConfigurationException;
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
    private final Map<String, Map<ExpenseType, List<DocumentType>>> ruleCache = new ConcurrentHashMap<>();

    public RuleEngine(StorageInterface storage) {
        this.storage = storage;
        this.mapper = new ObjectMapper();
    }

    public List<DocumentType> getMandatoryDocuments(String year, ExpenseType type) {
        // computeIfAbsent è atomico: se la funzione di mapping (loadRulesFromStorage) lancia un'eccezione,
        // nulla viene inserito nella mappa. Questo garantisce che errori temporanei non vengano cachati.
        Map<ExpenseType, List<DocumentType>> rulesForYear = ruleCache.computeIfAbsent(year, this::loadRulesFromStorage);
        return rulesForYear.getOrDefault(type, Collections.emptyList());
    }

    private Map<ExpenseType, List<DocumentType>> loadRulesFromStorage(String year) {
        String filename = "rules_" + year + ".json";

        // Nota: storage.existsFolder potrebbe lanciare eccezione se lo storage è giù.
        // Lasciamo che si propaghi per evitare caching di stati inconsistenti.
        if (!storage.existsFolder(CONFIG_FOLDER)) {
            try {
                storage.createFolder(CONFIG_FOLDER);
            } catch (Exception e) {
                logger.warn("Impossibile creare cartella config (possibile problema permessi/connessione)", e);
                // Non blocchiamo qui, proviamo comunque a leggere se possibile, altrimenti fallirà dopo.
            }
        }

        // FIX CRITICO: Gestione Errori "Fail-Safe"
        // Non usiamo try-catch che ritorna emptyMap() per errori generici.
        // Se c'è un errore di I/O (SMB down) o JSON malformato, dobbiamo lanciare eccezione.
        // In questo modo computeIfAbsent NON salva il valore e al prossimo giro si riprova.
        try (InputStream is = storage.loadFile(CONFIG_FOLDER, filename)) {
            return mapper.readValue(is, new TypeReference<Map<ExpenseType, List<DocumentType>>>() {});
        } catch (ConfigurationException e) {
            // Se è un errore di business/storage noto, lo rilanciamo così com'è
            logger.error("Errore critico caricamento regole per anno {}: {}", year, e.getMessage());
            throw e;
        } catch (RuntimeException e){
            logger.error("errore generico: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            // Se è un errore imprevisto (es. Jackson parsing fail), lo wrappiamo
            String msg = String.format("Errore lettura/parsing file regole %s/%s", CONFIG_FOLDER, filename);
            logger.error(msg, e);
            throw new ConfigurationException(msg, e);
        }
    }

    public void reloadRules() {
        ruleCache.clear();
        logger.info("Cache regole invalidata.");
    }
}