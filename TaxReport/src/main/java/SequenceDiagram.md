sequenceDiagram
participant UI as Client (GUI)
participant Core as ExpenseCoreService
participant Ent as ExpenseEntry
participant Slot as DocumentSlot
participant FS as FileSystem (Raspberry)

    %% Scenario: Utente ha già creato la voce "Dentista" ed è nello stato PARTIAL

    UI->>Core: uploadFile(entryID, DocType.INVOICE, byteStream)
    activate Core
    
    Core->>Core: retrieveEntry(entryID)
    
    Note right of Core: Identifica lo slot corretto
    Core->>Ent: getSlot(DocType.INVOICE)
    Ent-->>Core: slotRef
    
    Note right of Core: Costruisce path leggibile
    Note right of Core: .../Dentista/Fattura.pdf
    
    Core->>FS: saveFile(entry.folderPath, "Fattura.pdf", byteStream)
    FS-->>Core: Success
    
    Core->>Slot: fill(new FileMetadata(...))
    
    Note right of Core: Ricalcolo immediato stato
    Core->>Ent: updateStatus()
    alt All Mandatory Slots Full
        Ent->>Ent: status = COMPLIANT
    else
        Ent->>Ent: status = PARTIAL
    end
    
    Core-->>UI: UpdatedEntryDTO (Status: COMPLIANT)
    deactivate Core