package metier;

import metier.exceptions.PlayerNotAllowedException;
import metier.exceptions.UnknownSessionException;
import metier.exceptions.UnknownGameException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GestionnaireParties {
    private static final int MAX_ERREURS_PAR_DEFAUT = 7;
    private static final Duration DELAI_REPRISE_PAR_DEFAUT = Duration.ofDays(7);
    private static final Duration DELAI_PARTIE_TERMINEE_PAR_DEFAUT = Duration.ofMinutes(10);

    private final Dictionnaire dictionnaire;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger compteurParties = new AtomicInteger();
    private final Map<Integer, Partie> parties = new ConcurrentHashMap<>();

    public GestionnaireParties(Dictionnaire dictionnaire) {
        this.dictionnaire = dictionnaire;
    }

    // Crée une partie et y inscrit le joueur
    public Session creerPartie(Joueur joueur, int nbJoueursRequis) {
        return creerPartie(joueur, nbJoueursRequis, dictionnaire.genererMot());
    }

    // Mot choisi par l'appelant (mode compétitif par exemple)
    public Session creerPartie(Joueur joueur, int nbJoueursRequis, Mot mot) {
        Partie partie = new Partie(compteurParties.incrementAndGet(),
                mot, nbJoueursRequis, MAX_ERREURS_PAR_DEFAUT);
        return inscrire(partie, joueur);
    }

    // Rejoint une partie existante (multi)
    public Session rejoindrePartie(int numero, Joueur joueur) {
        Partie partie = parties.get(numero);
        if (partie == null) {
            throw new UnknownGameException("Partie inconnue ou supprimée");
        }
        return inscrire(partie, joueur);
    }

    private Session inscrire(Partie partie, Joueur joueur) {
        Session s = partie.rejoindre(joueur);
        parties.put(partie.getNumero(), partie);
        sessions.put(s.getIdentifiant(), s);
        return s;
    }

    public Session retrouver(String sessionId) {
        Session s = (sessionId == null) ? null : sessions.get(sessionId);
        if (s == null) {
            throw new UnknownSessionException("Session inconnue ou expirée");
        }
        return s;
    }

    // RESUME : id + jeton -> session reprise
    public Session reprendre(String sessionId, String jeton) {
        Session s = retrouver(sessionId);
        if (!s.verifierJeton(jeton)) {
            throw new PlayerNotAllowedException("Jeton invalide");
        }
        s.getPartie().reprendre(s);
        return s;
    }

    // Déconnexion (coupure réseau ou sortie)
    public void deconnecter(Session s) {
        s.getPartie().deconnecter(s);
    }

    // Valeurs du protocole (appelée par la tâche périodique du serveur)
    public int purgerExpirees() {
        return purgerExpirees(DELAI_REPRISE_PAR_DEFAUT, DELAI_PARTIE_TERMINEE_PAR_DEFAUT);
    }

    // Une seule durée pour les deux cas
    public int purgerExpirees(Duration delai) {
        return purgerExpirees(delai, delai);
    }

    // Supprime les sessions déconnectées depuis plus longtemps que le délai
    public int purgerExpirees(Duration delaiReprise, Duration delaiPartieTerminee) {
        Instant maintenant = Instant.now();
        Instant limiteReprise = maintenant.minus(delaiReprise);
        Instant limiteTerminee = maintenant.minus(delaiPartieTerminee);

        int retirees = 0;
        for (Session s : sessions.values()) {
            if (s.getPartie().expirer(s, limiteReprise)) {
                sessions.remove(s.getIdentifiant(), s);
                retirees++;
            }
        }
        for (Partie p : parties.values()) {
            if (p.peutEtreSupprimee(limiteTerminee)) {
                p.getSessions().forEach(s -> sessions.remove(s.getIdentifiant(), s));
                parties.remove(p.getNumero(), p);
            }
        }
        return retirees;
    }
}