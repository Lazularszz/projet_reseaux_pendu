package console;

import metier.Dictionnaire;
import metier.Joueur;
import metier.Partie;
import metier.Session;
import metier.enums.EtatPartie;
import metier.enums.ResultatCoup;
import metier.exceptions.PenduException;

import java.util.Scanner;

public class Main {

    // ===== Paramètres modifiables =====
    private static final int NUMERO_PARTIE = 1;
    private static final int NB_JOUEURS_REQUIS = 1;
    private static final int MAX_ERREURS = 7;
    private static final String PSEUDONYME = "Alice";

    private static final String COMMANDE_SAUVEGARDER = "!";
    private static final String COMMANDE_REPRENDRE = "?";

    public static void main(String[] args) {
        Dictionnaire dico = new Dictionnaire();
        Partie partie = new Partie(NUMERO_PARTIE, dico.genererMot(), NB_JOUEURS_REQUIS, MAX_ERREURS);
        Session session = partie.rejoindre(new Joueur(PSEUDONYME));

        System.out.println("Partie " + partie.getNumero() + " : " + partie.getEtat().getLibelle());
        System.out.println("Commandes : une lettre pour jouer, '" + COMMANDE_SAUVEGARDER
                + "' pour sauvegarder, '" + COMMANDE_REPRENDRE + "' pour reprendre.");

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