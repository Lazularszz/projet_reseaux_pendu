package console;

import metier.Dictionnaire;
import metier.GestionnaireParties;
import metier.Joueur;
import metier.Partie;
import metier.Session;
import java.time.Duration;
import metier.enums.EtatPartie;
import metier.enums.ResultatCoup;
import metier.exceptions.PenduException;

import java.util.Scanner;

public class Main {

    // ===== Paramètres modifiables =====
    private static final int NB_JOUEURS_REQUIS = 1;
    private static final String PSEUDONYME = "Alice";
    private static final Duration DELAI_EXPIRATION_TEST = Duration.ofSeconds(2);

    private static final String COMMANDE_SAUVEGARDER = "!";
    private static final String COMMANDE_REPRENDRE = "?";
    private static final String COMMANDE_PURGER = "#";

    public static void main(String[] args) {
        GestionnaireParties gestionnaire = new GestionnaireParties(new Dictionnaire());
        Session session = gestionnaire.creerPartie(new Joueur(PSEUDONYME), NB_JOUEURS_REQUIS);
        Partie partie = session.getPartie();

        // Ce que le client réseau mémoriserait pour reprendre
        String sessionId = session.getIdentifiant();
        String jeton = session.getJeton();

        System.out.println("Partie " + partie.getNumero() + " : " + partie.getEtat().getLibelle());
        System.out.println("Commandes : une lettre pour jouer, '" + COMMANDE_SAUVEGARDER
                + "' pour sauvegarder, '" + COMMANDE_REPRENDRE + "' pour reprendre, et '" + COMMANDE_PURGER + "' pour purger les sessions expirées.");

        Scanner sc = new Scanner(System.in);
        while (partie.getEtat() != EtatPartie.GAGNEE && partie.getEtat() != EtatPartie.PERDUE) {
            System.out.println("\nMot : " + partie.getMotAffiche()
                    + "  | erreurs restantes : " + partie.getErreursRestantes()
                    + "  | état : " + partie.getEtat().getLibelle());
            System.out.print("> ");
            String saisie = sc.nextLine().trim();

            try {
                if (saisie.equals(COMMANDE_SAUVEGARDER)) {
                    partie.deconnecter(session);
                    System.out.println("Session sauvegardée : " + partie.getEtat().getLibelle());
                } else if (saisie.equals(COMMANDE_REPRENDRE)) {
                    partie.reprendre(session);
                    System.out.println("Session reprise : " + partie.getEtat().getLibelle());
                } else if (saisie.equals(COMMANDE_PURGER)) {
                    int retirees = gestionnaire.purgerExpirees(DELAI_EXPIRATION_TEST);
                    System.out.println("Sessions expirées retirées : " + retirees);
                } else if (saisie.length() != 1) {
                    System.out.println("Une seule lettre attendue.");
                } else {
                    ResultatCoup r = partie.proposerLettre(session, saisie.charAt(0));
                    System.out.println(r.getLibelle());
                }
            } catch (PenduException e) {
                System.out.println("Erreur " + e.getCode() + " : " + e.getMessage());
            }
        }

        System.out.println("\nPartie terminée : " + partie.getEtat().getLibelle()
                + " (mot affiché : " + partie.getMotRevele() + ")");
    }
}