    package metier;

    import java.util.UUID;

    public class Joueur {
        private final String identifiant;
        private final String pseudonyme;

        public Joueur(String pseudonyme) {
            this.identifiant = UUID.randomUUID().toString();
            this.pseudonyme = pseudonyme;
        }

        public String getIdentifiant() {
            return this.identifiant;
        }

        public String getPseudonyme() {
            return this.pseudonyme;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Joueur j && identifiant.equals(j.identifiant);
        }

        @Override
        public int hashCode() {
            return identifiant.hashCode();
        }
    }
