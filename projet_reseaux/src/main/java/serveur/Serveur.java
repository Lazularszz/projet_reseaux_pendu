package serveur;

import metier.Dictionnaire;
import metier.GestionnaireParties;
import metier.Session;
import metier.exceptions.PenduException;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Point d'entrée du serveur : accepte les connexions (un thread virtuel par client)
// et purge périodiquement les sessions expirées.
public class Serveur {
    public static final int PORT_DEFAUT = 5000;

    private final GestionnaireParties gestionnaire = new GestionnaireParties(new Dictionnaire());
    // session_id -> connexion qui la possède actuellement
    private final Map<String, ClientHandler> proprietaires = new ConcurrentHashMap<>();

    GestionnaireParties getGestionnaire() {
        return gestionnaire;
    }

    // ===== Gestion des propriétaires de session =====
    // Ces méthodes sont synchronisées entre elles : elles évitent qu'une ancienne connexion
    // qui se ferme "déconnecte" une session qu'une nouvelle connexion vient de reprendre.

    synchronized void enregistrer(Session s, ClientHandler h) {
        proprietaires.put(s.getIdentifiant(), h);
    }

    // RESUME : vérifie le jeton, reprend la session et la retire à l'ancienne connexion
    synchronized Session reprendre(String sessionId, String jeton, ClientHandler h) {
        Session s = gestionnaire.reprendre(sessionId, jeton);
        ClientHandler ancien = proprietaires.put(sessionId, h);
        if (ancien != null && ancien != h) {
            ancien.oublier(sessionId);
        }
        return s;
    }

    // SAVE ou fermeture de connexion : déconnecte la session si h en est bien le propriétaire
    synchronized void liberer(Session s, ClientHandler h) {
        if (proprietaires.remove(s.getIdentifiant(), h)) {
            try {
                gestionnaire.deconnecter(s);
            } catch (PenduException ignore) {
                // session déjà expirée ou supprimée : rien à faire
            }
        }
    }

    // ===== Démarrage =====

    public void demarrer(int port) throws IOException {
        ScheduledExecutorService purge = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "purge");
            t.setDaemon(true);
            return t;
        });
        purge.scheduleAtFixedRate(() -> {
            try {
                int n = gestionnaire.purgerExpirees();
                if (n > 0) System.out.println("[purge] " + n + " session(s) expirée(s)");
            } catch (RuntimeException e) {
                e.printStackTrace();   // la purge ne doit jamais s'arrêter
            }
        }, 1, 1, TimeUnit.MINUTES);

        try (ServerSocket ecoute = new ServerSocket(port)) {
            System.out.println("Serveur du pendu en écoute sur le port " + port);
            while (true) {
                Socket socket = ecoute.accept();
                Thread.ofVirtual().start(new ClientHandler(socket, this));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : PORT_DEFAUT;
        new Serveur().demarrer(port);
    }
}