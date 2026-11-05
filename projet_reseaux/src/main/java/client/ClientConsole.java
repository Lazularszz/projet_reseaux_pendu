package client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import protocole.Codec;
import protocole.Framing;
import protocole.Message;
import protocole.TypeMessage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;

public class ClientConsole {

    // ===== Paramètres modifiables =====
    private static final String HOTE_PAR_DEFAUT = "localhost";
    private static final int PORT_PAR_DEFAUT = 5000;
    private static final String PSEUDONYME_PAR_DEFAUT = "Alice";

    // Les request_id doivent croître même après une reconnexion : partir de l'heure garantit
    // qu'on ne retombe jamais sur un id déjà mémorisé par le serveur.
    private static final AtomicLong COMPTEUR_REQUETES = new AtomicLong(System.currentTimeMillis());

    // Dernière partie connue (pour 'resume' sans arguments)
    private static volatile String sessionId;
    private static volatile String jeton;

    public static void main(String[] args) throws IOException {
        String hote = args.length > 0 ? args[0] : HOTE_PAR_DEFAUT;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : PORT_PAR_DEFAUT;
        String pseudo = args.length > 2 ? args[2] : PSEUDONYME_PAR_DEFAUT;

        try (Socket socket = new Socket(hote, port)) {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            demarrerLecture(in);
            envoyer(out, new Message(TypeMessage.HELLO, null, null, payload("pseudonym", pseudo)));

            System.out.println("Commandes : new [n] | <lettre> | save | resume [session_id token] | quit");
            Scanner sc = new Scanner(System.in);
            while (sc.hasNextLine()) {
                String ligne = sc.nextLine().trim();
                if (ligne.isEmpty()) continue;
                if (ligne.equals("quit")) break;
                try {
                    traiterCommande(out, ligne);
                } catch (IllegalArgumentException e) {
                    System.out.println("Commande invalide : " + e.getMessage());
                }
            }
        }
    }

    private static void traiterCommande(DataOutputStream out, String ligne) throws IOException {
        String[] mots = ligne.split("\\s+");
        switch (mots[0]) {
            case "new" -> {
                ObjectNode p = JsonNodeFactory.instance.objectNode();
                if (mots.length > 1) p.put("players", Integer.parseInt(mots[1]));
                envoyer(out, new Message(TypeMessage.NEW_GAME, prochainId(), null, p));
            }
            case "save" -> envoyer(out,
                    new Message(TypeMessage.SAVE, prochainId(), exigerSession(), null));
            case "resume" -> {
                String id = mots.length > 2 ? mots[1] : exigerSession();
                String tok = mots.length > 2 ? mots[2] : exigerJeton();
                envoyer(out, new Message(TypeMessage.RESUME, null, id, payload("token", tok)));
                sessionId = id;
                jeton = tok;
            }
            default -> {
                if (ligne.length() != 1) {
                    throw new IllegalArgumentException("une seule lettre ou une commande connue");
                }
                envoyer(out, new Message(TypeMessage.LETTER, prochainId(), exigerSession(),
                        payload("letter", ligne)));
            }
        }
    }

    // ===== Réception : un thread qui affiche tout ce que le serveur envoie =====

    private static void demarrerLecture(DataInputStream in) {
        Thread t = new Thread(() -> {
            try {
                String json;
                while ((json = Framing.lire(in)) != null) {
                    afficher(Codec.decoder(json));
                }
                System.out.println("[Connexion fermée par le serveur]");
            } catch (IOException e) {
                System.out.println("[Connexion perdue : " + e.getMessage() + "]");
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void afficher(Message m) {
        JsonNode p = m.getPayload();
        switch (m.getType()) {
            case TypeMessage.WELCOME -> System.out.println("Bienvenue " + p.path("pseudonym").asText());
            case TypeMessage.STATE -> {
                if (p.has("token")) {      // seulement dans la réponse à NEW_GAME
                    sessionId = m.getSessionId();
                    jeton = p.get("token").asText();
                    System.out.println("Session mémorisée : " + sessionId + " / jeton : " + jeton);
                }
                System.out.println("Mot : " + p.path("word").asText()
                        + " | état : " + p.path("state").asText()
                        + " | erreurs : " + p.path("errors").asInt()
                        + " (reste " + p.path("remaining").asInt() + ")"
                        + (p.has("result") ? " | coup : " + p.get("result").asText() : "")
                        + (p.has("revealed") ? " | mot complet : " + p.get("revealed").asText() : ""));
            }
            case TypeMessage.ERROR -> System.out.println("ERREUR " + p.path("code").asText()
                    + " : " + p.path("message").asText());
            default -> System.out.println("Message inattendu : " + m);
        }
    }

    // ===== Utilitaires =====

    private static void envoyer(DataOutputStream out, Message m) throws IOException {
        Framing.ecrire(out, Codec.encoder(m));
    }

    private static ObjectNode payload(String cle, String valeur) {
        ObjectNode p = JsonNodeFactory.instance.objectNode();
        p.put(cle, valeur);
        return p;
    }

    private static long prochainId() {
        return COMPTEUR_REQUETES.incrementAndGet();
    }

    private static String exigerSession() {
        if (sessionId == null) throw new IllegalArgumentException("aucune partie en cours : faites 'new'");
        return sessionId;
    }

    private static String exigerJeton() {
        if (jeton == null) throw new IllegalArgumentException("aucun jeton mémorisé");
        return jeton;
    }
}