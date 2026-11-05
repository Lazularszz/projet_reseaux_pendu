package serveur;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import metier.Joueur;
import metier.Partie;
import metier.Session;
import metier.enums.EtatPartie;
import metier.enums.ResultatCoup;
import metier.exceptions.InvalidLetterException;
import metier.exceptions.InvalidStateException;
import metier.exceptions.PenduException;
import metier.exceptions.PlayerNotAllowedException;
import protocole.Codec;
import protocole.Framing;
import protocole.Message;
import protocole.TypeMessage;
import protocole.exceptions.InvalidMessageException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Une instance par connexion cliente : lit les requêtes, appelle le métier, répond.
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Serveur serveur;
    private DataInputStream in;
    private DataOutputStream out;

    private Joueur joueur;   // créé au HELLO
    // sessions attachées à cette connexion (une connexion peut en porter plusieurs)
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    ClientHandler(Socket socket, Serveur serveur) {
        this.socket = socket;
        this.serveur = serveur;
    }

    @Override
    public void run() {
        String origine = String.valueOf(socket.getRemoteSocketAddress());
        System.out.println("[+] " + origine);
        try {
            socket.setKeepAlive(true);   // détecte à terme les connexions mortes
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            while (true) {
                String texte = Framing.lire(in);
                if (texte == null) break;   // fermeture propre par le client

                Message requete;
                try {
                    requete = Codec.decoder(texte);
                } catch (InvalidMessageException e) {
                    // le cadrage est intact, on peut continuer la conversation
                    envoyer(Message.erreur(null, e.getCode(), e.getMessage()));
                    continue;
                }
                traiter(requete);
            }
        } catch (IOException e) {
            // coupure réseau ou cadrage invalide : on ferme la connexion
        } finally {
            fermer();
            System.out.println("[-] " + origine);
        }
    }

    // ===== Aiguillage =====

    private void traiter(Message m) {
        try {
            if (!TypeMessage.REQUETES.contains(m.getType())) {
                throw new InvalidMessageException("Type non autorisé côté client : " + m.getType());
            }
            if (m.getType().equals(TypeMessage.HELLO)) {
                hello(m);
                return;
            }
            if (joueur == null) {
                throw new InvalidStateException("HELLO est obligatoire avant toute autre requête");
            }
            switch (m.getType()) {
                case TypeMessage.NEW_GAME -> nouvellePartie(m);
                case TypeMessage.LETTER -> lettre(m);
                case TypeMessage.SAVE -> sauvegarder(m);
                case TypeMessage.RESUME -> reprendre(m);
                default -> throw new InvalidMessageException("Type inconnu : " + m.getType());
            }
        } catch (PenduException e) {
            envoyer(Message.erreur(m.getRequestId(), e.getCode(), e.getMessage()));
        } catch (RuntimeException e) {
            e.printStackTrace();
            envoyer(Message.erreur(m.getRequestId(), "INTERNAL_ERROR", "Erreur interne du serveur"));
        }
    }

    // ===== Requêtes =====

    private void hello(Message m) {
        if (joueur != null) {
            throw new InvalidStateException("HELLO déjà effectué sur cette connexion");
        }
        joueur = new Joueur(m.texte("pseudonym"));
        envoyer(Message.welcome(joueur.getPseudonyme()));
    }

    private void nouvellePartie(Message m) {
        Integer n = m.entier("players");
        int nbJoueurs = (n == null) ? 1 : n;
        if (nbJoueurs != 1) {
            // première version : solo uniquement (le multi viendra avec JOIN_GAME)
            throw new InvalidStateException("Seules les parties solo sont disponibles pour le moment");
        }
        Session s = serveur.getGestionnaire().creerPartie(joueur, nbJoueurs);
        attacher(s);
        envoyer(etat(s, m.getRequestId(), null, true));   // seule réponse qui contient le jeton
    }

    private void lettre(Message m) {
        Session s = sessionAttachee(m.getSessionId());
        String texte = m.texte("letter");
        if (texte.length() != 1) {
            throw new InvalidLetterException("Une seule lettre est attendue");
        }
        char c = texte.charAt(0);
        // Rejouée avec le même request_id, la requête n'est exécutée qu'une fois
        ResultatCoup resultat = s.executerUneFois(m.getRequestId(),
                () -> s.getPartie().proposerLettre(s, c));
        envoyer(etat(s, m.getRequestId(), resultat, false));
    }

    private void sauvegarder(Message m) {
        Session s = sessionAttachee(m.getSessionId());
        s.executerUneFois(m.getRequestId(), () -> {
            serveur.liberer(s, this);   // déconnecte la session -> SAUVEGARDEE
            return Boolean.TRUE;
        });
        sessions.remove(s.getIdentifiant());
        envoyer(etat(s, m.getRequestId(), null, false));
    }

    private void reprendre(Message m) {
        // Vérifie id + jeton, reprend la session, et la retire à l'ancienne connexion éventuelle
        Session s = serveur.reprendre(m.getSessionId(), m.texte("token"), this);
        sessions.put(s.getIdentifiant(), s);
        envoyer(etat(s, m.getRequestId(), null, false));
    }

    // ===== Sessions attachées =====

    private void attacher(Session s) {
        sessions.put(s.getIdentifiant(), s);
        serveur.enregistrer(s, this);
    }

    // Appelé par Serveur quand une autre connexion a repris la session
    void oublier(String sessionId) {
        sessions.remove(sessionId);
    }

    private Session sessionAttachee(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s != null) return s;
        // Session inexistante -> UNKNOWN_SESSION (lancée par retrouver) ; sinon elle existe
        // mais n'appartient pas à cette connexion
        serveur.getGestionnaire().retrouver(sessionId);
        throw new PlayerNotAllowedException("Session non attachée à cette connexion");
    }

    // ===== Construction de la réponse STATE =====

    private Message etat(Session s, Long requestId, ResultatCoup resultat, boolean avecJeton) {
        Partie p = s.getPartie();
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        synchronized (p) {   // instantané cohérent (les méthodes de Partie sont synchronized)
            EtatPartie e = p.getEtat();
            n.put("state", e.name());
            n.put("word", p.getMotAffiche());
            n.put("remaining", p.getErreursRestantes());
            n.put("errors", p.getNbErreurs());
            n.put("letters", p.getLettresProposees());
            if (e == EtatPartie.GAGNEE || e == EtatPartie.PERDUE) {
                n.put("revealed", p.getMotRevele());   // jamais avant la fin
            }
        }
        if (resultat != null) n.put("result", resultat.name());
        if (avecJeton) n.put("token", s.getJeton());
        return new Message(TypeMessage.STATE, requestId, s.getIdentifiant(), n);
    }

    // ===== Réseau =====

    private void envoyer(Message m) {
        try {
            Framing.ecrire(out, Codec.encoder(m));
        } catch (IOException e) {
            fermer();
        }
    }

    private void fermer() {
        // Déconnecte toutes les sessions dont cette connexion est encore propriétaire
        for (Session s : sessions.values()) {
            serveur.liberer(s, this);
        }
        sessions.clear();
        try {
            socket.close();
        } catch (IOException ignore) { }
    }
}