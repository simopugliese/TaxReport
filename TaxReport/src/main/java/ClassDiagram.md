classDiagram
%% Configuration & Rules
class RuleSet {
+int taxYear
+List~CategoryDefinition~ categories
}

    class CategoryDefinition {
        +String id
        +String name
        +List~RequirementDef~ requirements
    }

    class RequirementDef {
        +DocType type
        +boolean mandatory
        +String outputFilename %% es. "Fattura.pdf"
    }

    %% Domain
    class ExpenseEntry {
        +UUID internalId
        +String description
        +LocalDate date
        +String folderPath
        +ValidationStatus status
        +List~DocumentSlot~ slots
        +updateStatus()
    }

    class DocumentSlot {
        +DocType type
        +boolean isMandatory
        +String expectedFilename
        +FileMetadata currentFile %% Null se vuoto
        +isSatisfied() boolean
    }
    
    class FileMetadata {
        +String path
        +long size
        +LocalDateTime uploadedAt
    }

    %% Manager
    class ExpenseCoreService {
        +createEntry(dto)
        +uploadFile(entryId, docType, fileContent)
        +deleteFile(entryId, docType)
        +getZipForAccountant(year)
    }

    %% Relationships
    RuleSet "1" *-- "*" CategoryDefinition
    CategoryDefinition "1" *-- "*" RequirementDef
    ExpenseEntry "1" *-- "*" DocumentSlot
    DocumentSlot "0..1" --> "1" FileMetadata
    ExpenseCoreService ..> RuleSet : reads
    ExpenseCoreService --> ExpenseEntry : manages