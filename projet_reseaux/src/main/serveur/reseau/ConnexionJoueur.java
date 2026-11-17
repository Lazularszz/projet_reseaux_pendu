package reseau;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import metier.Joueur;
import metier.ServeurPendu;
import metier.Session;
import metier.exceptions.InvalidLetterException;
import metier.exceptions.InvalidStateException;
import metier.exceptions.PlayerNotAllowedException;
import metier.exceptions.UnknownSessionException;

import java.net.Socket;

public class ConnexionJoueur {
    private final ServeurPendu serveur;
    private final Socket socketClient;
    private ThreadConnexion threadConnexion;

    private Joueur joueur;
    private Session sessionActive;
    private final Gson gson = new Gson();

    public ConnexionJoueur(ServeurPendu serveur, Socket socketClient) {
        this.serveur = serveur;
        this.socketClient = socketClient;
    }

    public void setThreadConnexion(ThreadConnexion threadConnexion) {
        this.threadConnexion = threadConnexion;
    }

    public Socket getSocket() {
        return socketClient;
    }

    public void envoyer(String message) {
        if (threadConnexion != null) {
            threadConnexion.envoyerMessage(message);
        }
    }

    //Permet de traiter les message reçu du client: segmente le message au format json et
    // le traite en fonction de son type (se référer au protocole)
    public void traiterMessage(String message) {
        if (message == null || message.isBlank()) {
            gererDeconnexion();
            return;
        }

        JsonObject messageJson;
        try {
            messageJson = gson.fromJson(message, JsonObject.class);
        } catch (JsonSyntaxException e) {
            envoyerErreur("INVALID_MESSAGE", "Le format du message n'est pas un JSON valide.");
            return;
        }

        if (!messageJson.has("type")) {
            envoyerErreur("INVALID_MESSAGE", "Le champ 'type' est obligatoire.");
            return;
        }

        String type = messageJson.get("type").getAsString().toUpperCase();
        Integer requestId = messageJson.has("request_id") ? messageJson.get("request_id").getAsInt() : null;

        try {
            switch (type) {
                case "HELLO" -> traiterHello(messageJson);
                case "NEW_GAME" -> traiterNewGame(messageJson, requestId);
                case "LETTER" -> traiterLetter(messageJson, requestId);
                case "RESUME" -> traiterResume(messageJson, requestId);
                case "QUIT" -> traiterQuit();
                default -> envoyerErreur("UNKNOWN_COMMAND", "Type de message inconnu : " + type);
            }
        } catch (InvalidStateException e) {
            envoyerErreur("INVALID_STATE", e.getMessage());
        } catch (InvalidLetterException e) {
            envoyerErreur("INVALID_LETTER", e.getMessage());
        } catch (UnknownSessionException e) {
            envoyerErreur("UNKNOWN_SESSION", e.getMessage());
        } catch (PlayerNotAllowedException e) {
            envoyerErreur("PLAYER_NOT_ALLOWED", e.getMessage());
        } catch (Exception e) {
            envoyerErreur("SERVER_ERROR", "Erreur interne : " + e.getMessage());
        }
    }

    private void gererDeconnexion() {
    }

    private void traiterHello(JsonObject messageJson) {
        String nom = messageJson.has("payload")&& messageJson.getAsJsonObject("payload").has("nom")?
                messageJson.getAsJsonObject("payload").get("nom").getAsString()
                : "Joueur Anonyme";


    }

    private void traiterNewGame(JsonObject messageJson, Integer requestId) {
    }

    private void traiterLetter(JsonObject messageJson, Integer requestId) {
    }

    private void traiterResume(JsonObject messageJson, Integer requestId) {
    }

    private void traiterQuit() {
        gererDeconnexion();
        if (threadConnexion != null) {
            threadConnexion.fin();
        }
    }

    private void envoyerErreur(String unknownCommand, String s) {
    }


}
