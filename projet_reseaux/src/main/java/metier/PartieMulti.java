package metier;

import metier.enums.EtatPartie;
import metier.enums.ResultatCoup;
import metier.exceptions.PlayerNotAllowedException;

import java.util.List;

// Partie à deux joueurs : mot partagé, tour à tour.
// Celui qui complète le mot gagne ; si le pendu est complet, celui qui a fait la dernière erreur perd.
// Tout passe par les points d'extension de Partie : la classe Partie n'est pas modifiée.
public class PartieMulti extends Partie {
    private final String code;
    private int indexTour;        // position, dans la liste des sessions, du joueur qui doit jouer
    private Session vainqueur;    // null tant que la partie n'est pas terminée

    public PartieMulti(int numero, Mot mot, int nbJoueursRequis, int maxErreursAcceptees, String code) {
        super(numero, mot, nbJoueursRequis, maxErreursAcceptees);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    // Session dont c'est le tour (null tant que personne n'a rejoint)
    public synchronized Session getJoueurCourant() {
        List<Session> sessions = getSessions();
        return sessions.isEmpty() ? null : sessions.get(indexTour % sessions.size());
    }

    // Session gagnante (null tant que la partie n'est pas terminée)
    public synchronized Session getVainqueur() {
        return vainqueur;
    }

    @Override
    protected void verifierCoupAutorise(Session s) {
        if (getJoueurCourant() != s) {
            throw new PlayerNotAllowedException("Ce n'est pas votre tour");
        }
    }

    @Override
    protected void apresCoup(Session s, ResultatCoup r) {
        switch (r) {
            case GAGNANT -> vainqueur = s;
            case PERDANT -> vainqueur = autreQue(s);
            default -> indexTour = (indexTour + 1) % getNbJoueursRequis();
        }
    }

    // Forfait : le joueur resté dans la partie gagne.
    // Si l'adversaire est connecté, il ne reçoit pas de notification immédiate (notifierTous est
    // privée dans Partie) : il verra la fin de partie à sa prochaine requête.
    @Override
    protected void apresExpiration(Session s) {
        EtatPartie e = getEtat();
        if (e == EtatPartie.GAGNEE || e == EtatPartie.PERDUE) return;
        Session autre = autreQue(s);
        if (autre != null && !autre.estExpiree()) {
            vainqueur = autre;
            terminer(EtatPartie.PERDUE);
        }
    }

    private Session autreQue(Session s) {
        return getSessions().stream().filter(x -> x != s).findFirst().orElse(null);
    }
}