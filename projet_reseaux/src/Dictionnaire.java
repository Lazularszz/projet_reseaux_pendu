import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class Dictionnaire {
    private ArrayList<String> dictionnaire;

    public Dictionnaire() {
        this.dictionnaire = new ArrayList<>(List.of("mot", "jeu", "souris", "chat", "dragon"));
    }

    public ArrayList<String> getDictionnaire(){return this.dictionnaire;}

    public ArrayList<String> ajouterMot(String mot){
        this.dictionnaire.add(mot);
    }


}

