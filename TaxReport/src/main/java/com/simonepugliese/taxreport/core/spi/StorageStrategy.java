package com.simonepugliese.taxreport.core.spi;

import com.simonepugliese.taxreport.core.exception.StorageException;
import java.io.InputStream;

public interface StorageStrategy {

    /**
     * Crea una cartella nel percorso specificato.
     * @param relativePath Il percorso relativo (es. "/2024/RSS.../Dentista")
     * @return true se creata, false se esisteva già
     * @throws StorageException se c'è un errore fisico di scrittura
     */
    boolean createDirectory(String relativePath);

    /**
     * Verifica se un percorso (file o cartella) esiste.
     */
    boolean exists(String relativePath);

    /**
     * Salva uno stream di dati su disco.
     * @param fullPath Il percorso della cartella (es. "/2024/RSS.../Dentista")
     * @param filename Il nome del file (es. "Fattura.pdf")
     * @param content Lo stream di input (deve essere letto e chiuso dall'implementazione)
     * @throws StorageException se la scrittura fallisce
     */
    void saveFile(String fullPath, String filename, InputStream content);

    /**
     * Cancella un file fisico.
     * @param fullPath Il percorso della cartella.
     * @param filename Il nome del file da cancellare.
     */
    void deleteFile(String fullPath, String filename);
}