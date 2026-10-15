package metier;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class Dictionnaire {
    private final List<String> dictionnaire;

    public Dictionnaire() {
        this.dictionnaire = new CopyOnWriteArrayList<>(List.of("mot", "jeu", "souris", "chat", "dragon"));
    }

    // Copie immuable : l'appelant ne peut pas modifier la liste interne
    public List<String> getDictionnaire() {
        return List.copyOf(this.dictionnaire);
    }

    public void ajouterMot(String mot) {
        if (mot == null || mot.isBlank()) {
            throw new IllegalArgumentException("Mot vide");
        }
        dictionnaire.add(mot.trim().toLowerCase());
    }

    public Mot genererMot() {
        String motAleatoire = this.dictionnaire.get(
                ThreadLocalRandom.current().nextInt(this.dictionnaire.size())
        );
        return new Mot(motAleatoire);
    }
}

