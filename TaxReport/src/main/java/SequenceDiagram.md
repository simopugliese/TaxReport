sequenceDiagram
participant Client
participant ExpManager as ExpenseManager
participant Config as RuleConfig (JSON)
participant FS as StorageService
participant DB as Repository

    Client->>ExpManager: createExpense(Year, Member, Category, "Dentista")
    
    activate ExpManager
    ExpManager->>Config: getRuleFor(Category)
    Config-->>ExpManager: ComplianceRule (e.g., Needs Receipt+Payment)
    
    Note right of ExpManager: 1. Calcolo Path Fisico
    
    ExpManager->>FS: createFolder("/2024/RSSMRA.../Sanitaria/UUID/")
    
    alt FS Creation Fails
        FS-->>ExpManager: Error
        ExpManager-->>Client: Exception (Rollback, nothing saved)
    else FS Creation OK
        FS-->>ExpManager: Success
        
        Note right of ExpManager: 2. Persistenza Metadati
        
        create participant Entry as new ExpenseEntry
        ExpManager->>Entry: new(Status=EMPTY)
        ExpManager->>DB: save(Entry)
        DB-->>ExpManager: OK
        
        ExpManager-->>Client: ExpenseCreatedDTO (Status: EMPTY)
    end
    deactivate ExpManager