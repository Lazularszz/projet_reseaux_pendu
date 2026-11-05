package client;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JeuController implements EcouteurClient {

    // ===== Paramètres modifiables =====
    private static final int NB_JOUEURS_PAR_DEFAUT = 1;
    private static final int NB_IMAGES_PENDU = 8;     // pendu0.png ... pendu7.png

    // ===== Éléments du FXML (noms = fx:id) =====
    @FXML private BorderPane rootPane;
    @FXML private Label commentaire;
    @FXML private Label labelMot;
    @FXML private Label affichageErreurs;
    @FXML private ImageView imagePendu;

    private final ClientReseau reseau = new ClientReseau(this);
    private final Map<Character, Button> boutons = new HashMap<>();
    private final Set<Character> lettresJouees = new HashSet<>();
    private Button derniereLettre;
    private boolean enJeu;

    // Appelée automatiquement par le FXMLLoader
    @FXML
    private void initialize() {
        // Les 26 boutons de lettres (texte d'une seule lettre)
        for (Node n : rootPane.lookupAll(".button")) {
            if (n instanceof Button b && b.getText() != null && b.getText().length() == 1) {
                boutons.put(Character.toUpperCase(b.getText().charAt(0)), b);
            }
        }
        activer(false);

        // Barre du haut : actions de partie
        Button nouvelle = new Button("Nouvelle partie");
        nouvelle.setOnAction(e -> nouvellePartie());
        Button sauver = new Button("Sauvegarder");
        sauver.setOnAction(e -> reseau.sauvegarder());
        Button reprendre = new Button("Reprendre");
        reprendre.setOnAction(e -> reprendrePartie());
        HBox haut = (HBox) rootPane.getTop();
        haut.setSpacing(8);
        haut.setPadding(new Insets(8));
        haut.getChildren().addAll(nouvelle, sauver, reprendre);

        // Clavier : taper une lettre équivaut à cliquer sur le bouton
        rootPane.sceneProperty().addListener((obs, ancienne, scene) -> {
            if (scene != null) {
                scene.addEventHandler(KeyEvent.KEY_TYPED, e -> {
                    String c = e.getCharacter();
                    if (c.length() == 1 && Character.isLetter(c.charAt(0))) jouer(c.charAt(0));
                });
            }
        });

        labelMot.setText("");
        affichageErreurs.setText("");
        commentaire.setText("Connexion au serveur…");
    }

    // Appelée par l'application une fois la fenêtre ouverte
    public void demarrer(String hote, int port, String pseudonyme) {
        Thread t = new Thread(() -> {
            try {
                reseau.connecter(hote, port, pseudonyme);
                Platform.runLater(() -> commentaire.setText(
                        "Connecté. Lancez une nouvelle partie" + (reseau.peutReprendre()
                                ? " ou reprenez la précédente." : ".")));
            } catch (IOException e) {
                Platform.runLater(() -> commentaire.setText("Serveur injoignable : " + e.getMessage()));
            }
        }, "connexion");
        t.setDaemon(true);
        t.start();
    }

    public void fermer() {
        reseau.close();
    }

    // ===== Actions de l'utilisateur =====

    // Handler déclaré dans le FXML (onAction="#ChoisirLettre")
    @FXML
    private void ChoisirLettre(ActionEvent event) {
        Button b = (Button) event.getSource();
        jouer(b.getText().charAt(0));
    }

    private void jouer(char lettre) {
        Button b = boutons.get(Character.toUpperCase(lettre));
        if (!enJeu || b == null || b.isDisabled()) return;
        b.setDisable(true);
        derniereLettre = b;
        lettresJouees.add(Character.toUpperCase(lettre));
        reseau.proposerLettre(lettre);
    }

    private void nouvellePartie() {
        lettresJouees.clear();
        reseau.nouvellePartie(NB_JOUEURS_PAR_DEFAUT);
    }

    private void reprendrePartie() {
        if (reseau.peutReprendre()) {
            reseau.reprendre();
        } else {
            commentaire.setText("Aucune partie à reprendre.");
        }
    }

    // ===== Réponses du serveur (thread réseau -> thread JavaFX) =====

    @Override
    public void surWelcome(String pseudonyme) {
        Platform.runLater(() -> commentaire.setText("Bienvenue " + pseudonyme + " !"));
    }

    @Override
    public void surEtat(EtatJeu e) {
        Platform.runLater(() -> afficher(e));
    }

    @Override
    public void surErreur(String code, String message) {
        Platform.runLater(() -> {
            commentaire.setText("Erreur " + code + " : " + message);
            if (derniereLettre != null && enJeu) {    // le coup a été refusé : on réactive la lettre
                derniereLettre.setDisable(false);
                lettresJouees.remove(derniereLettre.getText().charAt(0));
            }
        });
    }

    @Override
    public void surDeconnexion(String raison) {
        Platform.runLater(() -> {
            enJeu = false;
            activer(false);
            commentaire.setText(raison + ". Relancez l'application, puis « Reprendre ».");
        });
    }

    // ===== Affichage =====

    private void afficher(EtatJeu e) {
        int max = e.erreurs() + e.restantes();
        affichageErreurs.setText("Erreurs : " + e.erreurs() + " / " + max);
        afficherPendu(e.erreurs(), max);
        lettresJouees.clear();
        for (char c : e.lettres().toCharArray()) {
            lettresJouees.add(Character.toUpperCase(c));
        }

        switch (e.etat()) {
            case "EN_COURS" -> {
                enJeu = true;
                labelMot.setText(espacer(e.mot()));
                commentaire.setText(texteDuCoup(e.resultat()));
            }
            case "EN_ATTENTE" -> {
                enJeu = false;
                labelMot.setText(espacer(e.mot()));
                commentaire.setText("En attente d'autres joueurs…");
            }
            case "SAUVEGARDEE" -> {
                enJeu = false;
                labelMot.setText(espacer(e.mot()));
                commentaire.setText("Partie sauvegardée. Vous pourrez la reprendre plus tard.");
            }
            case "GAGNEE" -> {
                enJeu = false;
                labelMot.setText(espacer(e.mot()));
                commentaire.setText("Gagné ! Bravo !");
            }
            case "PERDUE" -> {
                enJeu = false;
                labelMot.setText(espacer(e.motComplet() != null ? e.motComplet() : e.mot()));
                commentaire.setText("Perdu… le mot était « " + e.motComplet() + " ».");
            }
            default -> commentaire.setText("État inconnu : " + e.etat());
        }
        activer(enJeu);
    }

    private String texteDuCoup(String resultat) {
        if (resultat == null) return "À vous de jouer !";
        return switch (resultat) {
            case "CORRECTE" -> "Bien joué !";
            case "INCORRECTE" -> "Raté…";
            case "DEJA_PROPOSEE" -> "Lettre déjà proposée.";
            default -> "À vous de jouer !";
        };
    }

    // "_e__e" -> "_ e _ _ e" (plus lisible)
    private String espacer(String mot) {
        return String.join(" ", mot.split(""));
    }

    // Active les boutons de lettres, sauf celles déjà jouées
    private void activer(boolean actif) {
        for (Map.Entry<Character, Button> entree : boutons.entrySet()) {
            entree.getValue().setDisable(!actif || lettresJouees.contains(entree.getKey()));
        }
    }

    private void afficherPendu(int erreurs, int max) {
        int index = (max <= 0) ? 0 : Math.min(NB_IMAGES_PENDU - 1, erreurs * (NB_IMAGES_PENDU - 1) / max);
        URL url = getClass().getResource("/images/pendu" + index + ".png");
        imagePendu.setImage(url == null ? null : new Image(url.toExternalForm()));
    }
}