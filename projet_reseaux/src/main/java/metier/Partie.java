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
        return s;
    }

    public synchronized void deconnecter(Session s) {
        verifierAppartenance(s);
        s.marquerDeconnectee();
        recalculerEtat();
    }

    public synchronized void reprendre(Session s) {
        verifierAppartenance(s);
        s.marquerConnectee();
        recalculerEtat();
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
        if (!Character.isLetter(lettre)) {
            throw new InvalidLetterException("Une seule lettre est attendue.");
        }
        lettre = Character.toLowerCase(lettre);

        if (!lettresProposees.add(lettre)) {
            return ResultatCoup.DEJA_PROPOSEE;
        }
        demarree = true;

        if (mot.contient(lettre)) {
            if (mot.estDecouvert()) {
                etat = EtatPartie.GAGNEE;
                return ResultatCoup.GAGNANT;
            }
            return ResultatCoup.CORRECTE;
        }
        nbErreurs++;
        if (nbErreurs >= maxErreursAcceptees) {
            etat = EtatPartie.PERDUE;
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
}
