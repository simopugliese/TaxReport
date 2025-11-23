package com.simonepugliese.taxreport.core.impl;

import com.simonepugliese.taxreport.core.api.ExpenseService;
import com.simonepugliese.taxreport.core.domain.RuleEngine;
import com.simonepugliese.taxreport.core.spi.MetadataRepository;
import com.simonepugliese.taxreport.core.spi.StorageStrategy;

/**
 * Factory statica per assemblare il Core.
 * In futuro qui potrai leggere una config (properties) per decidere
 * se istanziare il "RemoteStorage" (Raspberry) o "LocalStorage".
 */
public class TaxReportFactory {

    public static ExpenseService createLocalService(String rootPath) {
        // 1. Strategie Concrete
        StorageStrategy storage = new FileSystemStorageStrategy(rootPath);
        MetadataRepository repo = new InMemoryMetadataRepository(); // TODO: Sostituire con JsonRepository o SqlRepository

        // 2. Componenti Interni
        RuleEngine engine = new RuleEngine();

        // 3. Dependency Injection manuale
        return new ExpenseServiceImpl(storage, repo, engine);
    }
}
