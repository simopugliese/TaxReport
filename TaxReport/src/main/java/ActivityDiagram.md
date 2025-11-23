flowchart TD
start([Trigger: Open App / File Change]) --> fetch[Fetch Expense Entry]
fetch --> loadRule[Load ComplianceRule for Category]
loadRule --> scanFS[Scan Physical Folder]

    subgraph ValidationLoop [Check Requirements]
        direction TB
        scanFS --> checkReq{Has Next Requirement?}
        checkReq -- Yes --> getReq[Get Requirement <br/>(e.g., 'Bonifico')]
        getReq --> findFile{File Exists in Folder?}
        findFile -- Yes --> markFound[Mark Req Satisfied]
        findFile -- No --> markMissing[Mark Req MISSING]
        markFound --> checkReq
        markMissing --> checkReq
    end
    
    checkReq -- No --> evalStatus{Are all Mandatory <br/>Reqs Satisfied?}
    
    evalStatus -- Yes --> setC[Set Status: COMPLIANT]
    evalStatus -- No --> setP[Set Status: PARTIAL / WARNING]
    
    setC --> updateDB[Update DB Metadata]
    setP --> updateDB
    
    updateDB --> stop([End])