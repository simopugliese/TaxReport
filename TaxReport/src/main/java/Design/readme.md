com.taxreport
├── core
│   ├── api                 <-- PUBBLICO (Visibile alla GUI)
│   │   ├── ExpenseService.java  (Interfaccia)
│   │   └── TaxReportFactory.java (Factory per istanziare il servizio)
│   ├── dto                 <-- PUBBLICO (Data Transfer Objects)
│   │   ├── NewExpenseDTO.java
│   │   ├── ExpenseStatusDTO.java
│   │   └── Enums...
│   ├── exception           <-- PUBBLICO (Gerarchia Errori)
│   ├── spi                 <-- PUBBLICO (Service Provider Interfaces per Plugin)
│   │   ├── StorageStrategy.java
│   │   └── MetadataRepository.java
│   ├── domain              <-- PACKAGE PROTECTED (Logica interna)
│   │   ├── ExpenseEntry.java
│   │   ├── DocumentSlot.java
│   │   └── RuleEngine.java
│   └── impl                <-- PACKAGE PROTECTED (Il motore)
│       ├── ExpenseServiceImpl.java
│       ├── FileSystemStorage.java
│       └── JsonRepository.java