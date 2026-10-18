package metier;

public class Mot {
    private final String motCache;
    private final char[] motAffiche;

    public Mot(String mot) {
        this.motCache = mot.toLowerCase()   ;
        this.motAffiche = new char[motCache.length()];
        java.util.Arrays.fill(this.motAffiche, '_');
    }

    // Réservé au serveur, ne jamais l'envoyer au client
    public String getMotCache() {
        return this.motCache;
    }

    public String getMotAffiche() {
        return new String(this.motAffiche);
    }

    // Dis si la lettre est dans le mot, et révèle toutes ses occurrences
    // Accès restreint au package metier : seule Partie doit l'appeler.
    /* package-private */ boolean contient(char lettre) {
        lettre = Character.toLowerCase(lettre);
        boolean trouve = false;
        for(int i = 0; i < getMotCache().length(); i++) {
            if(getMotCache().charAt(i) == lettre) {
                this.motAffiche[i] = lettre;
                trouve = true;
            }
        }
        return trouve;
    }

    public boolean estDecouvert() {
        for (char c : motAffiche) {
            if  (c == '_') { return false; }
        }
        return true;
    }
}
