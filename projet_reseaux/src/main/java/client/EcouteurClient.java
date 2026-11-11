package client;

// Appelé depuis le thread de lecture réseau : l'interface doit repasser par Platform.runLater.
public interface EcouteurClient {
    void surWelcome(String pseudonyme);
    void surEtat(EtatJeu etat);
    void surErreur(String code, String message);
    void surDeconnexion(String raison);
}