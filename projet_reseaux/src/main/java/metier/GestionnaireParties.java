package metier;

import metier.exceptions.PlayerNotAllowedException;
import metier.exceptions.UnknownSessionException;
import metier.exceptions.UnknownGameException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.security.SecureRandom;
import java.util.Locale;

public class GestionnaireParties {
    private static final int MAX_ERREURS_PAR_DEFAUT = 7;
    private static final Duration DELAI_REPRISE_PAR_DEFAUT = Duration.ofDays(7);
    private static final Duration DELAI_PARTIE_TERMINEE_PAR_DEFAUT = Duration.ofMinutes(10);
    private static final String ALPHABET_CODE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";  // sans 0, O, 1, I
    private static final int LONGUEUR_CODE = 6;
    private static final SecureRandom RNG = new SecureRandom();

    private final Map<String, PartieMulti> partiesParCode = new ConcurrentHashMap<>();
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
        int numero = compteurParties.incrementAndGet();
        Partie partie;
        if (nbJoueursRequis == 1) {
            partie = new Partie(numero, mot, 1, MAX_ERREURS_PAR_DEFAUT);
        } else {
            PartieMulti multi = new PartieMulti(numero, mot, nbJoueursRequis,
                    MAX_ERREURS_PAR_DEFAUT, genererCode());
            partiesParCode.put(multi.getCode(), multi);
            partie = multi;
        }
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

    // Rejoint une partie à deux joueurs à partir de son code (non sensible à la casse)
    public Session rejoindrePartieParCode(String code, Joueur joueur) {
        PartieMulti partie = (code == null) ? null
                : partiesParCode.get(code.trim().toUpperCase(Locale.ROOT));
        if (partie == null) {
            throw new UnknownGameException("Code de partie inconnu");
        }
        return inscrire(partie, joueur);
    }

    private String genererCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(LONGUEUR_CODE);
            for (int i = 0; i < LONGUEUR_CODE; i++) {
                sb.append(ALPHABET_CODE.charAt(RNG.nextInt(ALPHABET_CODE.length())));
            }
            code = sb.toString();
        } while (partiesParCode.containsKey(code));
        return code;
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
                if (p instanceof PartieMulti m) {
                    partiesParCode.remove(m.getCode(), m);
                }
            }
        }
        return retirees;
    }
}