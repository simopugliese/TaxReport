package com.simonepugliese.taxreport;

public class mainDB {
    public static void main(String[] args) {
        // Crea il DB nella cartella corrente del progetto
        new com.simonepugliese.taxreport.core.impl.SqlMetadataRepository(".");
        System.out.println("DB creato! Ora puoi collegarlo all'IDE.");
    }
}