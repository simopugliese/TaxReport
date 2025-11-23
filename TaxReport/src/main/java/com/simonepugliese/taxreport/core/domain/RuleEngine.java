package com.simonepugliese.taxreport.core.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.simonepugliese.taxreport.core.dto.DocType;
import com.simonepugliese.taxreport.core.dto.ValidationStatus;
import com.simonepugliese.taxreport.core.exception.ConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Singleton o Managed Bean (instanziato dal Service)
public class RuleEngine {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CategoryRule> rules = new ConcurrentHashMap<>();
    private boolean initialized = false;

    /**
     * Carica il file rules_{year}.json dal classpath.
     */
    public void loadRules(int year) {
        String resourceName = "/rules_" + year + ".json";
        try (InputStream is = getClass().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new ConfigurationException("File regole non trovato: " + resourceName);
            }
            List<CategoryRule> loadedRules = mapper.readValue(is, new TypeReference<>() {});

            this.rules.clear();
            for (CategoryRule r : loadedRules) {
                this.rules.put(r.id(), r);
            }
            this.initialized = true;
        } catch (IOException e) {
            throw new ConfigurationException("Errore parsing regole JSON per l'anno " + year + ": " + e.getMessage());
        }
    }

    /**
     * Inizializza gli slot di una nuova spesa in base alla categoria.
     */
    public void applyRules(ExpenseEntry entry) {
        ensureInitialized();
        CategoryRule rule = rules.get(entry.getCategoryId());
        if (rule == null) {
            throw new ConfigurationException("Categoria sconosciuta: " + entry.getCategoryId());
        }

        for (Requirement req : rule.requirements()) {
            entry.addSlot(new DocumentSlot(req.type(), req.mandatory(), req.filenameConvention()));
        }
    }

    /**
     * Ritorna il nome file standard per quel tipo di documento (es. "Fattura.pdf").
     * Nota: Questo è un helper generico. In un sistema più complesso dipenderebbe dalla Category.
     * Per ora usiamo una convenzione statica o definita nel primo requisito trovato.
     */
    public String getStandardFilename(DocType type) {
        return switch (type) {
            case INVOICE -> "Fattura.pdf";
            case RECEIPT -> "Scontrino.pdf";
            case PAYMENT -> "Pagamento.pdf";
            case PRESCRIPTION -> "Ricetta.pdf";
            case REPORT -> "Referto.pdf";
        };
    }

    /**
     * Cuore della validazione: decide il colore del semaforo.
     */
    public ValidationStatus validate(ExpenseEntry entry) {
        if (entry.getStatus() == ValidationStatus.LOCKED) {
            return ValidationStatus.LOCKED;
        }

        boolean anyFilePresent = false;
        boolean allMandatoryPresent = true;

        for (DocumentSlot slot : entry.getSlots().values()) {
            boolean isFilled = slot.isFilled();

            if (isFilled) {
                anyFilePresent = true;
            }

            if (slot.isMandatory() && !isFilled) {
                allMandatoryPresent = false;
            }
        }

        if (!anyFilePresent) return ValidationStatus.EMPTY;
        if (!allMandatoryPresent) return ValidationStatus.PARTIAL;
        return ValidationStatus.COMPLIANT;
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new ConfigurationException("RuleEngine non inizializzato. Chiamare initYear() prima.");
        }
    }

    record CategoryRule(String id, String description, List<Requirement> requirements) {}

    record Requirement(DocType type, boolean mandatory, String filenameConvention) {}
}
