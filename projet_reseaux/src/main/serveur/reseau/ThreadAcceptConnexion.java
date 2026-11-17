package reseau;

import metier.ServeurException;
import metier.ServeurPendu;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ThreadAcceptConnexion extends Thread {
    private ServeurPendu serveur;
    private ServerSocket serverSocket;
    private boolean enExecution = true;

    public ThreadAcceptConnexion(ServeurPendu serveur) {
        this.serveur = serveur;
        try {
            this.serverSocket = new ServerSocket(serveur.getPort());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        start();
    }

    @Override
    public void run() {
        while(enExecution) {
            try {
                Socket socket = serverSocket.accept();
                System.out.println("Connexion !");

                ConnexionJoueur connexion = new ConnexionJoueur(serveur, socket);
                serveur.add(connexion);

            } catch (IOException e) {
                // Si enExecution est false, c'est un arrêt volontaire via arreter()
                if (!enExecution) {
                    System.out.println("Arrêt de l'écoute des connexions.");
                    break;
                }
                System.err.println("Erreur lors de l'acceptation d'un client : " + e.getMessage());
            } catch (ServeurException e) {
                System.err.println("Erreur applicative serveur : " + e.getMessage());
            }
        }
    }

    public void arreter() {
        this.enExecution = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
    }
}
