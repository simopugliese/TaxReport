classDiagram
%% Domain Entities
class TaxYear {
+int year
+boolean isClosed
}

    class FamilyMember {
        +String fiscalCode
        +String fullName
    }

    class ExpenseCategory {
        +String id
        +String name
        +ComplianceRule rule
    }

    class ExpenseEntry {
        +UUID id
        +String description
        +Date date
        +ExpenseStatus status
        +List~Document~ documents
        +validate()
    }

    class Document {
        +String fileName
        +String path
        +DocumentType type
    }

    %% Rule Engine (Configuration)
    class ComplianceRule {
        +String description
        +List~Requirement~ requirements
        +check(List~Document~) boolean
    }

    class Requirement {
        +DocumentType requiredType
        +boolean isMandatory
    }

    %% Interfaces & Services
    class IExpenseRepository {
        <<interface>>
        +save(ExpenseEntry)
        +findByYear(int)
    }

    class IStorageService {
        <<interface>>
        +createFolder(path)
        +listFiles(path)
    }

    class ExpenseManager {
        +createExpense(DTO)
        +refreshStatus(ExpenseEntry)
    }

    %% Relationships
    TaxYear "1" *-- "*" ExpenseEntry
    FamilyMember "1" o-- "*" ExpenseEntry
    ExpenseEntry "*" --> "1" ExpenseCategory
    ExpenseCategory "1" --> "1" ComplianceRule
    ComplianceRule "1" *-- "*" Requirement
    ExpenseEntry "1" *-- "*" Document
    
    ExpenseManager --> IExpenseRepository
    ExpenseManager --> IStorageService
    ExpenseManager ..> ComplianceRule : uses