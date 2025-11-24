package pugliesesimone.taxreport.metadata;

import pugliesesimone.taxreport.model.Expense;

import java.util.Optional;
import java.util.UUID;

public interface MetadataInterface {
    public void save(Expense expense);
    public Optional<Expense> findById(UUID id);
}
