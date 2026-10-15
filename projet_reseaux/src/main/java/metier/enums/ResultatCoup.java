package metier.enums;

public enum ResultatCoup {
    CORRECTE("Correcte"),
    INCORRECTE("Incorrecte"),
    GAGNANT("Coup gagnant"),
    PERDANT("Coup perdant"),
    DEJA_PROPOSEE("Déjà proposée");

    private final String libelle;

    ResultatCoup(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
