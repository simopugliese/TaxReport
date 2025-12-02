package pugliesesimone.taxreport.metadata;

import pugliesesimone.taxreport.model.Expense;
import pugliesesimone.taxreport.model.Person;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetadataInterface {
    void save(Expense expense);
    void savePerson(Person person);
    Optional<Expense> findById(UUID id);
    List<Expense> findByYear(String year);
}