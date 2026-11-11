package client;

import javafx.application.Application;

// Classe de démarrage distincte de l'Application : évite l'erreur
// « JavaFX runtime components are missing » quand on lance depuis l'IDE.
public class Lanceur {
    public static void main(String[] args) {
        Application.launch(AppPendu.class, args);
    }
}