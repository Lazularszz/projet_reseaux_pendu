package metier;

import reseau.ConnexionJoueur;
import reseau.ThreadAcceptConnexion;
import reseau.ThreadConnexion;

import java.util.ArrayList;

public class ServeurPendu {
    private final int port = 8080;


    private final GestionnaireParties gestionnaireParties;

    public ServeurPendu(Dictionnaire dictionnaire) {
        this.gestionnaireParties = new GestionnaireParties(dictionnaire);
    }

    public int getPort() {
        return port;
    }

    public GestionnaireParties getGestionnaireParties() {
        return gestionnaireParties;
    }

    public synchronized void add(ConnexionJoueur connexion) throws ServeurException {
        ThreadConnexion threadIO = new ThreadConnexion(connexion, connexion.getSocket());
        connexion.setThreadConnexion(threadIO);
        threadIO.start();
    }





}
