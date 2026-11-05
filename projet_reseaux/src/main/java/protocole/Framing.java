package protocole;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import protocole.exceptions.TailleInvalideException;

public final class Framing {
    public static final int TAILLE_MAX = 65_536;

    private Framing() { }

    // Écrit [longueur sur 4 octets][message UTF-8].
    // synchronized : un thread de réponse et un thread de notification peuvent
    // écrire sur la même socket, leurs octets ne doivent pas s'entremêler.
    public static void ecrire(DataOutputStream out, String json) throws IOException {
        byte[] octets = json.getBytes(StandardCharsets.UTF_8);
        if (octets.length > TAILLE_MAX) {
            throw new IllegalArgumentException("Message trop long : " + octets.length);
        }
        synchronized (out) {
            out.writeInt(octets.length);
            out.write(octets);
            out.flush();
        }
    }

    // Lit exactement un message. Renvoie null si le correspondant a fermé la connexion.
    public static String lire(DataInputStream in) throws IOException {
        int taille;
        try {
            taille = in.readInt();
        } catch (EOFException e) {
            return null;
        }
        if (taille <= 0 || taille > TAILLE_MAX) {
            throw new TailleInvalideException(taille);
        }
        byte[] octets = new byte[taille];
        in.readFully(octets);   // boucle jusqu'à avoir reçu les N octets
        return new String(octets, StandardCharsets.UTF_8);
    }
}