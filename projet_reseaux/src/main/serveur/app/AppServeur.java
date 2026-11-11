package app;

import metier.Dictionnaire;
import metier.ServeurPendu;
import reseau.ThreadAcceptConnexion;


public class AppServeur {
    public static void main(String[] args) {
        Dictionnaire dico = new Dictionnaire();
        ServeurPendu serveur = new ServeurPendu(dico);

        ThreadAcceptConnexion ecouteur = new ThreadAcceptConnexion(serveur);
        ecouteur.start();

    }
}
