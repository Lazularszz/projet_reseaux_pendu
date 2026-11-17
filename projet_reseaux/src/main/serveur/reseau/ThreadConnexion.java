package reseau;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ThreadConnexion extends Thread {
    private final Socket socket;
    private final ConnexionJoueur connexion;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean fin = false;

    public ThreadConnexion(ConnexionJoueur connexion, Socket socket) {
        this.connexion = connexion;
        this.socket = socket;
        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Erreur d'initialisation des flux réseau : " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            String message;
            // readLine() renvoie null si la connexion est fermée côté client
            while (!fin && (message = in.readLine()) != null) {
                if (!message.trim().isEmpty()) {
                    connexion.traiterMessage(message);
                }
            }
        } catch (IOException e) {
            if (!fin) {
                System.out.println("Interruption de la connexion pour le client : " + socket.getRemoteSocketAddress());
            }
        } finally {
            fermer();
        }
    }

    public void envoyerMessage(String message) {
        if (out != null && !socket.isClosed() && message != null) {
            out.println(message); // Framing par délimiteur '\n'
        }
    }

    public void fin() {
        this.fin = true;
        fermer();
    }

    private void fermer() {
        this.fin = true;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        // Notification au gestionnaire de connexion pour notifier la déconnexion
        connexion.gererDeconnexion();
    }
}
