package metier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import metier.enums.EtatPartie;
import metier.enums.ResultatCoup;
import metier.exceptions.InvalidLetterException;
import metier.exceptions.InvalidStateException;
import metier.exceptions.PlayerNotAllowedException;
import java.time.Instant;
import metier.exceptions.UnknownSessionException;

public class Partie {
    private final int numero;
    private final List<Session> sessions = new ArrayList<>();
    private final int nbJoueursRequis;
    private final Mot mot;
    private EtatPartie etat;
    private final Set<Character> lettresProposees = new HashSet<>() ;
    private int nbErreurs;
    private final int maxErreursAcceptees;
    private boolean demarree; // Au moins un coup joué
    private Instant termineeLe;   // null tant que la partie n'est pas terminée

    public Partie(int numero, Mot mot, int nbJoueursRequis, int maxErreursAcceptees) {
        this.numero = numero;
        this.mot = mot;
        this.nbJoueursRequis = nbJoueursRequis;
        this.maxErreursAcceptees = maxErreursAcceptees;
        this.etat = EtatPartie.EN_ATTENTE;
    }

    // ===== Getters =====

    public int getNumero() {
        return numero;
    }

    public synchronized EtatPartie getEtat() {
        return etat;
    }

    public synchronized String getMotAffiche() {
        return mot.getMotAffiche();
    }

    public synchronized String getMotRevele() {
        if (etat != EtatPartie.GAGNEE && etat != EtatPartie.PERDUE) {
            throw new InvalidStateException("Le mot n'est révélé qu'en fin de partie");
        }
        return mot.getMotCache();
    }

    public synchronized int getNbErreurs() {
        return nbErreurs;
    }

    public synchronized int getErreursRestantes() {
        return maxErreursAcceptees - nbErreurs;
    }

    public int getNbJoueursRequis() {
        return nbJoueursRequis;
    }

    public synchronized List<Session> getSessions() {
        return List.copyOf(sessions);       // copie : l'extérieur ne modifie pas la liste interne
    }

    // ===== Joueurs =====

    public synchronized Session rejoindre(Joueur j) {
        if (etat == EtatPartie.GAGNEE || etat == EtatPartie.PERDUE) {
            throw new InvalidStateException("La partie est terminée");
        }
        if (sessions.size() >= nbJoueursRequis) {
            throw new PlayerNotAllowedException("Partie complète");
        }
        if (sessions.stream().anyMatch(s -> s.getJoueur().equals(j))) {
            throw new PlayerNotAllowedException("Ce joueur est déjà dans la partie");
        }
        Session s = new Session(j, this);
        sessions.add(s);
        recalculerEtat();
        notifierTous();
        return s;
    }

    public synchronized void deconnecter(Session s) {
        verifierAppartenance(s);
        if (!s.estConnectee()) return;
        s.marquerDeconnectee();
        recalculerEtat();
        notifierTous();
    }

    public synchronized void reprendre(Session s) {
        verifierAppartenance(s);
        if (s.estExpiree()) {
            throw new UnknownSessionException("Session expirée");
        }
        s.marquerConnectee();
        recalculerEtat();
        notifierTous();
    }

    private void verifierAppartenance(Session s) {
        if (s == null || s.getPartie() != this) {
            throw new PlayerNotAllowedException("Session étrangère à cette partie");
        }
    }

    private void verifierConnectee(Session s) {
        if (!s.estConnectee()) {
            throw new PlayerNotAllowedException("Session déconnectée");
        }
    }

    // ===== Jeu =====

    public synchronized ResultatCoup proposerLettre(Session s, char lettre) {
        verifierAppartenance(s);
        verifierConnectee(s);
        if (etat != EtatPartie.EN_COURS) {
            throw new InvalidStateException("La partie n'est pas en cours : " + etat);
        }
        verifierCoupAutorise(s);                       // point d'extension
        if (!Character.isLetter(lettre)) {
            throw new InvalidLetterException("Une seule lettre est attendue.");
        }
        lettre = Character.toLowerCase(lettre);

        if (!lettresProposees.add(lettre)) {
            return ResultatCoup.DEJA_PROPOSEE;
        }
        demarree = true;
        ResultatCoup r = appliquer(lettre);
        apresCoup(s, r);                               // point d'extension
        notifierTous();
        return r;
    }

    private ResultatCoup appliquer(char lettre) {
        if (mot.contient(lettre)) {
            if (mot.estDecouvert()) {
                terminer(EtatPartie.GAGNEE);
                return ResultatCoup.GAGNANT;
            }
            return ResultatCoup.CORRECTE;
        }
        nbErreurs++;
        if (nbErreurs >= maxErreursAcceptees) {
            terminer(EtatPartie.PERDUE);
            return ResultatCoup.PERDANT;
        }
        return ResultatCoup.INCORRECTE;
    }

    // ===== Etat =====

    private void recalculerEtat() {
        if (etat == EtatPartie.GAGNEE || etat == EtatPartie.PERDUE) return;

        long presents = sessions.stream().filter(Session::estConnectee).count();

        if (presents == 0 && demarree) {
            etat = EtatPartie.SAUVEGARDEE;
        } else if (presents == nbJoueursRequis) {
            etat = EtatPartie.EN_COURS;
        } else {
            etat = EtatPartie.EN_ATTENTE;
        }
    }

    // ===== Points d'extension (vides = comportement solo) =====
// Redéfinis plus tard par une sous-classe : tour de jeu, chrono, forfait...
    protected void verifierCoupAutorise(Session s) { }
    protected void apresCoup(Session s, ResultatCoup r) { }
    protected void apresExpiration(Session s) { }

    // Fin de partie, utilisable aussi par une sous-classe (forfait, temps écoulé)
    protected void terminer(EtatPartie fin) {
        etat = fin;
        termineeLe = Instant.now();
    }

    // ===== Expiration, appelée par le gestionnaire =====
    /* package-private */ synchronized boolean expirer(Session s, Instant limite) {
        verifierAppartenance(s);
        Instant depuis = s.getDeconnecteDepuis();
        if (s.estConnectee() || s.estExpiree() || depuis == null || !depuis.isBefore(limite)) {
            return false;
        }
        s.marquerExpiree();
        apresExpiration(s);
        return true;
    }

    /* package-private */ synchronized boolean peutEtreSupprimee(Instant limite) {
        boolean terminee = termineeLe != null && termineeLe.isBefore(limite);
        boolean abandonnee = !sessions.isEmpty() && sessions.stream().allMatch(Session::estExpiree);
        return terminee || abandonnee;
    }

    private void notifierTous() {
        for (Session s : sessions) {
            if (s.estConnectee()) s.notifier(this);
        }
    }
}
