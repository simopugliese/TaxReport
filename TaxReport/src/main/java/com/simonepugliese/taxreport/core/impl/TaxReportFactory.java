package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.api.ExpenseService;
import com.simonepugliese.taxreport.core.domain.RuleEngine;
import com.simonepugliese.taxreport.core.spi.MetadataRepository;
import com.simonepugliese.taxreport.core.spi.StorageStrategy;

public class TaxReportFactory {

    // 1. SCENARIO "MAMMA" (PC Locale + Persistenza Reale)
    public static ExpenseService createLocalService(String rootPath) {
        // Usa FileSystem vero e JSON DB vero
        StorageStrategy storage = new FileSystemStorageStrategy(rootPath);
        MetadataRepository repo = new JsonMetadataRepository(rootPath);
        RuleEngine engine = new RuleEngine();

        return new ExpenseServiceImpl(storage, repo, engine);
    }

    // 2. SCENARIO "SVILUPPO/TEST" (Tutto in RAM, niente file sporchi)
    public static ExpenseService createVolatileService(String rootPath) {
        // Usa FileSystem vero (per vedere i PDF) ma DB in RAM (per non sporcare il JSON)
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