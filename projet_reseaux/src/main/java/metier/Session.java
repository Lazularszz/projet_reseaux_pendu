package metier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public class Session {
    private static final SecureRandom RNG = new SecureRandom();

    private final String identifiant;
    private final String jeton;
    private final Joueur joueur;
    private final Partie partie;
    private volatile boolean connecte;
    private volatile Instant deconnecteDepuis;      // null tant que le joueur est connecté

    // Accès restreint au package metier : seule Partie doit l'appeler.
    /* package-private */ Session(Joueur joueur, Partie partie) {
        this.identifiant = UUID.randomUUID().toString();
        this.jeton = genererJeton();
        this.joueur = joueur;
        this.partie = partie;
        this.connecte = true;
    }

    public String getIdentifiant() {
        return this.identifiant;
    }

    // À n'envoyer qu'au propriétaire, à la création de la session.
    // Jamais dans un STATE, jamais dans les logs.
    public String getJeton() {
        return this.jeton;
    }

    public Joueur getJoueur() {
        return joueur;
    }

    public Partie getPartie() {
        return partie;
    }

    public boolean estConnectee() {
        return connecte;
    }

    public Instant getDeconnecteDepuis() {
        return deconnecteDepuis;
    }

    private static String genererJeton() {
        byte[] octets = new byte[16];
        RNG.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    // Comparaison en temps constant
    // (évite qu'un attaquant puisse se servir du temps de traitement avant rejet pour déduire
    // combien de caractères au début de son essai sont corrects)
    public boolean verifierJeton(String fourni) {
        if (fourni == null) return false;
        return MessageDigest.isEqual(
                jeton.getBytes(StandardCharsets.UTF_8),
                fourni.getBytes(StandardCharsets.UTF_8));
    }

    /* package-private */ void marquerConnectee() {
        connecte = true;
        deconnecteDepuis = null;
    }

    /* package-private */ void marquerDeconnectee() {
        connecte = false;
        deconnecteDepuis = Instant.now();
    }
}
