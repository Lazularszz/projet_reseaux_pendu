package client;

// Ce que l'interface affiche, extrait d'un message STATE
public record EtatJeu(String etat, String mot, int erreurs, int restantes,
                      String lettres, String resultat, String motComplet) { }