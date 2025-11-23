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
    // Cache multi-anno: Anno -> (CategoriaID -> Regola)
    private final Map<Integer, Map<String, CategoryRule>> rulesCache = new ConcurrentHashMap<>();

    /**
     * Carica le regole per un anno specifico se non sono già in cache.
     */
    public void loadRules(int year) {
        if (rulesCache.containsKey(year)) {
            return; // Già caricate, risparmiamo I/O
        }

        String resourceName = "/rules_" + year + ".json";
        try (InputStream is = getClass().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new ConfigurationException("File regole non trovato: " + resourceName);
            }
            List<CategoryRule> loadedRules = mapper.readValue(is, new TypeReference<>() {});

            Map<String, CategoryRule> yearMap = new ConcurrentHashMap<>();
            for (CategoryRule r : loadedRules) {
                yearMap.put(r.id(), r);
            }
            rulesCache.put(year, yearMap);

        } catch (IOException e) {
            throw new ConfigurationException("Errore parsing regole JSON per l'anno " + year + ": " + e.getMessage());
        }
    }

    /**
     * Inizializza gli slot di una nuova spesa usando l'anno della spesa stessa.
     */
    public void applyRules(ExpenseEntry entry) {
        int year = entry.getDate().getYear();

        // Assicura che le regole per quell'anno siano caricate
        loadRules(year);

        Map<String, CategoryRule> yearRules = rulesCache.get(year);
        CategoryRule rule = yearRules.get(entry.getCategoryId());

        if (rule == null) {
            throw new ConfigurationException("Categoria sconosciuta '" + entry.getCategoryId() + "' per l'anno " + year);
        }

        for (Requirement req : rule.requirements()) {
            entry.addSlot(new DocumentSlot(req.type(), req.mandatory(), req.filenameConvention()));
        }
    }

    /**
     * Valida lo stato della spesa basandosi sugli slot riempiti.
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

    // Record interni mappati sul JSON
    record CategoryRule(String id, String description, List<Requirement> requirements) {}
    record Requirement(DocType type, boolean mandatory, String filenameConvention) {}
}