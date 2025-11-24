package pugliesesimone.taxreport.model;

import java.util.UUID;

public class Person {
    private final UUID id;
    private final String name;
    private final String fiscalCode;

    public Person(String name, String fiscalCode) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.fiscalCode = fiscalCode;
    }

    public Person(UUID id, String name, String fiscalCode) {
        this.id = id;
        this.name = name;
        this.fiscalCode = fiscalCode;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFiscalCode() {
        return fiscalCode;
    }
}
