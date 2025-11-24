package pugliesesimone.taxreport.metadata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pugliesesimone.taxreport.exception.PersonNotFoundException;
import pugliesesimone.taxreport.model.*;

import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MariaDbMetadataTest {

    @Mock Connection connection;
    @Mock PreparedStatement preparedStatement;
    @Mock Statement statement; // [FIX] Aggiunto mock per lo Statement (usato in initDatabase)
    @Mock ResultSet resultSet;

    MariaDbMetadata metadata;

    @BeforeEach
    void setUp() throws SQLException {
        // [FIX] Istruiamo la connessione PRIMA di chiamare il costruttore.
        // initDatabase() chiamerà connection.createStatement(), dobbiamo assicurarci che non ritorni null.
        // Usiamo lenient() perché initDatabase viene chiamato nel costruttore e Mockito potrebbe
        // lamentarsi se lo stubbing avviene "fuori" dal test method in modalità strict,
        // o se viene chiamato più volte.
        lenient().when(connection.createStatement()).thenReturn(statement);

        // Creiamo una classe anonima per sovrascrivere getConnection()
        metadata = new MariaDbMetadata("localhost", 3306, "db", "u", "p") {
            @Override
            protected Connection getConnection() {
                return connection;
            }
        };
    }

    @Test
    void save_ShouldExecuteUpdateAndCommit() throws SQLException {
        // Arrange
        Expense expense = new Expense("2024", new Person("P", "CF"), ExpenseType.VISITA_MEDICA, "D", "Data");
        expense.addDocument(new Document(DocumentType.FATTURA, "path"));

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        // Act
        metadata.save(expense);

        // Assert
        verify(connection).setAutoCommit(false);
        verify(preparedStatement, atLeast(2)).executeUpdate();
        verify(preparedStatement, times(1)).executeBatch();
        verify(connection).commit();
    }

    @Test
    void save_ShouldRollback_WhenSqlExceptionOccurs() throws SQLException {
        Expense expense = new Expense("2024", new Person("P", "CF"), ExpenseType.VISITA_MEDICA, "D", "Data");

        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Generic Error"));

        assertThrows(RuntimeException.class, () -> metadata.save(expense));

        verify(connection).rollback();
    }

    @Test
    void save_ShouldThrowPersonNotFound_WhenFkError1452() throws SQLException {
        Expense expense = new Expense("2024", new Person("P", "CF"), ExpenseType.VISITA_MEDICA, "D", "Data");

        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("FK Error", "State", 1452));

        assertThrows(PersonNotFoundException.class, () -> metadata.save(expense));
        verify(connection).rollback();
    }
}