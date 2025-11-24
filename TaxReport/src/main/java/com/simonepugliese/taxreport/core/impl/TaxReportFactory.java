package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.api.ExpenseService;
import com.simonepugliese.taxreport.core.domain.RuleEngine;
import com.simonepugliese.taxreport.core.spi.MetadataRepository;
import com.simonepugliese.taxreport.core.spi.StorageStrategy;

public class TaxReportFactory {

    /**
     * Versione CLASSICA (Mamma / Piccoli volumi)
     * - Usa JSON per i metadati (comodo da leggere a mano)
     * - Usa FileSystem per i PDF
     */
    public static ExpenseService createLocalService(String rootPath) {
        StorageStrategy storage = new FileSystemStorageStrategy(rootPath);
        MetadataRepository repo = new JsonMetadataRepository(rootPath);
        RuleEngine engine = new RuleEngine();

        return new ExpenseServiceImpl(storage, repo, engine);
    }

    /**
     * Versione SCALABILE (Produzione / 3000+ documenti)
     * - Usa SQLite per i metadati (veloce, transazionale)
     * - Usa FileSystem per i PDF
     */
    public static ExpenseService createSqlService(String rootPath) {
        StorageStrategy storage = new FileSystemStorageStrategy(rootPath);
        // Qui usiamo la nuova implementazione SQL
        MetadataRepository repo = new SqlMetadataRepository(rootPath);
        RuleEngine engine = new RuleEngine();

        return new ExpenseServiceImpl(storage, repo, engine);
    }

    /**
     * Versione TEST (Tutto in RAM)
     * - Non scrive nulla su disco (utile per unit test)
     */
    public static ExpenseService createVolatileService(String rootPath) {
        StorageStrategy storage = new FileSystemStorageStrategy(rootPath);
        MetadataRepository repo = new InMemoryMetadataRepository();
        RuleEngine engine = new RuleEngine();

        return new ExpenseServiceImpl(storage, repo, engine);
    }

    // 3. SCENARIO "RASPBERRY" (Che implementeremo dopo)
    public static ExpenseService createRaspberryService(String ip, String user, String pass, String remotePath) {
        // Qui useremo la strategia SSH (RemoteStorageStrategy) che dobbiamo ancora scrivere
        // StorageStrategy storage = new RemoteStorageStrategy(ip, user, pass, remotePath);

        // Il DB dei metadati dove lo mettiamo?
        // Opzione A: Sul PC locale (JsonMetadataRepository locale)
        // Opzione B: Sul Raspberry (dovremmo scaricarlo/caricarlo via SSH)

        // Per ora metto un placeholder, ma vedi come è facile aggiungere nuovi modi?
        return null; // TODO: Implementare RemoteStorageStrategy
    }
}