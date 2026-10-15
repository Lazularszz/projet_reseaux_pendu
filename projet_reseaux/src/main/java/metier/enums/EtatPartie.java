package metier.enums;

public enum EtatPartie {
    EN_ATTENTE("En attente"),
    EN_COURS("En cours"),
    GAGNEE("Gagnée"),
    PERDUE("Perdue"),
    SAUVEGARDEE("Sauvegardée");

    private final String libelle;

    EtatPartie(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
