stateDiagram-v2
[*] --> EMPTY : Created (Folder created)

    state validation_check <<choice>>

    EMPTY --> PARTIAL : File Uploaded
    PARTIAL --> validation_check : File Uploaded/Removed
    
    validation_check --> PARTIAL : Missing Mandatory Docs
    validation_check --> COMPLIANT : All Mandatory Docs Present
    
    COMPLIANT --> PARTIAL : File Removed
    
    COMPLIANT --> LOCKED : Year Closed / Sent to Accountant
    PARTIAL --> LOCKED : Year Closed (Warn User)
    
    LOCKED --> [*]