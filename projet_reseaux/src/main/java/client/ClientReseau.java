package client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import protocole.Codec;
import protocole.Framing;
import protocole.exceptions.InvalidMessageException;
import protocole.Message;
import protocole.TypeMessage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;

public class ClientReseau implements AutoCloseable {
    // Session mémorisée sur le disque : permet de reprendre après avoir fermé l'application
    private static final Preferences PREFS = Preferences.userNodeForPackage(ClientReseau.class);
    private static final String CLE_SESSION = "session_id";
    private static final String CLE_JETON = "token";

    private final EcouteurClient ecouteur;

    // Les request_id doivent croître même après une reconnexion : on part de l'heure.
    private final AtomicLong requetes = new AtomicLong(System.currentTimeMillis());

    // Un seul thread d'envoi : l'interface ne bloque jamais sur la socket
    private final ExecutorService envois = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "envois-client");
        t.setDaemon(true);
        return t;
    });

    private Socket socket;
    private DataOutputStream out;
    private volatile boolean ferme;
    private volatile String sessionId;
    private volatile String jeton;

    public ClientReseau(EcouteurClient ecouteur) {
        this.ecouteur = ecouteur;
        this.sessionId = PREFS.get(CLE_SESSION, null);
        this.jeton = PREFS.get(CLE_JETON, null);
    }

    // ===== Connexion =====

    public void connecter(String hote, int port, String pseudonyme) throws IOException {
        socket = new Socket(hote, port);
        out = new DataOutputStream(socket.getOutputStream());
        demarrerLecture(new DataInputStream(socket.getInputStream()));
        envoyer(new Message(TypeMessage.HELLO, null, null, payload("pseudonym", pseudonyme)));
    }

    @Override
    public void close() {
        ferme = true;
        envois.shutdownNow();
        try {
            if (socket != null) socket.close();
        } catch (IOException ignore) { }
    }

    // ===== Actions demandées par l'interface =====

    public void nouvellePartie(int nbJoueurs) {
        ObjectNode p = JsonNodeFactory.instance.objectNode();
        p.put("players", nbJoueurs);
        envoyer(new Message(TypeMessage.NEW_GAME, prochainId(), null, p));
    }

    public void proposerLettre(char lettre) {
        if (sessionId == null) return;
        envoyer(new Message(TypeMessage.LETTER, prochainId(), sessionId,
                payload("letter", String.valueOf(lettre).toLowerCase())));
    }

    public void sauvegarder() {
        if (sessionId == null) return;
        envoyer(new Message(TypeMessage.SAVE, prochainId(), sessionId, null));
    }

    public boolean peutReprendre() {
        return sessionId != null && jeton != null;
    }

    public void reprendre() {
        if (!peutReprendre()) return;
        envoyer(new Message(TypeMessage.RESUME, null, sessionId, payload("token", jeton)));
    }

    // ===== Réception =====

    private void demarrerLecture(DataInputStream in) {
        Thread t = new Thread(() -> {
            String raison = "Connexion fermée par le serveur";
            try {
                String json;
                while ((json = Framing.lire(in)) != null) {
                    try {
                        traiter(Codec.decoder(json));
                    } catch (InvalidMessageException e) {
                        // message illisible : ignoré
                    }
                }
            } catch (IOException e) {
                raison = "Connexion perdue : " + e.getMessage();
            }
            if (!ferme) ecouteur.surDeconnexion(raison);
        }, "lecture-client");
        t.setDaemon(true);
        t.start();
    }

    private void traiter(Message m) {
        switch (m.getType()) {
            case TypeMessage.WELCOME -> ecouteur.surWelcome(m.texte("pseudonym"));
            case TypeMessage.STATE -> {
                JsonNode p = m.getPayload();
                if (p.hasNonNull("token") && m.getSessionId() != null) {   // réponse à NEW_GAME
                    memoriser(m.getSessionId(), p.get("token").asText());
                }
                EtatJeu e = new EtatJeu(
                        p.path("state").asText(),
                        p.path("word").asText(),
                        p.path("errors").asInt(),
                        p.path("remaining").asInt(),
                        p.path("letters").asText(""),
                        p.hasNonNull("result") ? p.get("result").asText() : null,
                        p.hasNonNull("revealed") ? p.get("revealed").asText() : null);
                if (e.etat().equals("GAGNEE") || e.etat().equals("PERDUE")) {
                    oublier();   // partie finie : rien à reprendre
                }
                ecouteur.surEtat(e);
            }
            case TypeMessage.ERROR -> {
                String code = m.texte("code");
                if ("UNKNOWN_SESSION".equals(code)) oublier();   // session expirée
                ecouteur.surErreur(code, m.texte("message"));
            }
            default -> { }
        }
    }

    // ===== Utilitaires =====

    private void envoyer(Message m) {
        envois.execute(() -> {
            try {
                Framing.ecrire(out, Codec.encoder(m));
            } catch (IOException e) {
                if (!ferme) ecouteur.surDeconnexion("Envoi impossible : " + e.getMessage());
            }
        });
    }

    private void memoriser(String id, String token) {
        sessionId = id;
        jeton = token;
        PREFS.put(CLE_SESSION, id);
        PREFS.put(CLE_JETON, token);
    }

    private void oublier() {
        sessionId = null;
        jeton = null;
        PREFS.remove(CLE_SESSION);
        PREFS.remove(CLE_JETON);
    }

    private ObjectNode payload(String cle, String valeur) {
        ObjectNode p = JsonNodeFactory.instance.objectNode();
        p.put(cle, valeur);
        return p;
    }

    private long prochainId() {
        return requetes.incrementAndGet();
    }
}