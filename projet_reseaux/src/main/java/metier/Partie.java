package metier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import metier.enums.EtatPartie;
import metier.enums.ResultatCoup;

public class Partie {
    private final int numero;
    private final List<Joueur> listeJoueurs = new ArrayList<Joueur>();
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

    // ===== Getters

    public int getNumero() {
        return numero;
    }

    public EtatPartie getEtat() {
        return etat;
    }

    public String getMotAffiche() {
        return mot.getMotAffiche();
    }

    public int getNbErreurs() {
        return nbErreurs;
    }

    public int getErreursRestantes() {
        return maxErreursAcceptees - nbErreurs;
    }

    public int getNbJoueursRequis() {
        return nbJoueursRequis;
    }

    public synchronized List<Joueur> getListeJoueurs() {
        return List.copyOf(listeJoueurs);   // copie : l'extérieur ne modifie pas la liste interne
    }

    // ===== Joueurs =====

    public synchronized void ajouterJoueur(Joueur j) {
        if (listeJoueurs.size() >= nbJoueursRequis) {
            throw new IllegalStateException("Partie complète");
        }
        listeJoueurs.add(j);
        j.setConnecte(true);
        recalculerEtat();
    }

    public synchronized void deconnecter(Joueur j) {
        j.setConnecte(false);
        recalculerEtat();
    }

    public synchronized void reprendre(Joueur j) {
        j.setConnecte(true);
        recalculerEtat();
    }

    // ===== Jeu =====

    public synchronized ResultatCoup proposerLettre(char lettre) {
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

        long presents = listeJoueurs.stream().filter(Joueur::estConnecte).count();

        if (presents == 0 && demarree) {
            etat = EtatPartie.SAUVEGARDEE;
        } else if (presents == nbJoueursRequis) {
            etat = EtatPartie.EN_COURS;
        } else {
            etat = EtatPartie.EN_ATTENTE;
        }
    }
}
