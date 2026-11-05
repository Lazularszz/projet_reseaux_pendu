package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class AppPendu extends Application {

    // ===== Paramètres modifiables =====
    private static final String HOTE_PAR_DEFAUT = "localhost";
    private static final int PORT_PAR_DEFAUT = 5000;
    private static final String PSEUDONYME_PAR_DEFAUT = "Joueur";

    @Override
    public void start(Stage stage) throws IOException {
        JeuController controleur = new JeuController();
        FXMLLoader loader = new FXMLLoader(AppPendu.class.getResource("/fxml/jeu_pendu.fxml"));
        loader.setController(controleur);     // le FXML n'a pas de fx:controller
        Parent racine = loader.load();

        stage.setTitle("Jeu du pendu");
        stage.setScene(new Scene(racine));
        stage.setOnCloseRequest(e -> controleur.fermer());
        stage.show();

        // Arguments facultatifs : hôte port pseudonyme
        List<String> args = getParameters().getRaw();
        String hote = args.size() > 0 ? args.get(0) : HOTE_PAR_DEFAUT;
        int port = args.size() > 1 ? Integer.parseInt(args.get(1)) : PORT_PAR_DEFAUT;
        String pseudo = args.size() > 2 ? args.get(2) : PSEUDONYME_PAR_DEFAUT;
        controleur.demarrer(hote, port, pseudo);
    }
}