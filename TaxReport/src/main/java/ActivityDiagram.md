flowchart TD
start([Create Expense Request]) --> input{Validate Input}

    input --> |Valid| cleanDesc[Sanitize Description]
    Note right of cleanDesc: Rimuovi spazi, accenti, char speciali
    
    cleanDesc --> buildName[Build Folder Name: <br/>YYYY-MM-DD_SanitizedDesc]
    
    buildName --> checkDup{Folder Exists?}
    
    checkDup -- Yes --> appendIndex[Append suffix _(1), _(2)]
    appendIndex --> checkDup
    
    checkDup -- No --> mkDir[FS: Create Directory]
    
    mkDir -- Success --> initSlots[Init DocumentSlots from Rules]
    mkDir -- Fail --> error([Throw IO Exception])
    
    initSlots --> persist[DB: Save Metadata]
    persist --> finish([Return Entry ID])