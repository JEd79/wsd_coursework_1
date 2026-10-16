import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/*
 * WebServer.java
 * --------------
 * The MAIN class and entry point of our DVLA transactions web service.
 *
 * It follows exactly the same shape as the lecturer's "HttpServer" example:
 *   1. Open a ServerSocket on a port.
 *   2. In a loop, wait for a client to connect (accept()).
 *   3. For each connection, start a new thread (RequestHandler) to deal with it.
 *   4. Go back to waiting for the next client.
 *
 * The only extra thing we do is load the CSV data ONCE before the loop starts,
 * and share that one TransactionStore with every request thread.
 */
public class WebServer {

    // Settings for this coursework.
    private static final int PORT = 8181;            // the port we listen on
    private static final String ROOT = "/2356784";   // our student-number URI root
    private static final String CSV_FILE = "dvla_transactions.csv";

    public static void main(String[] args) {

        // ---- Load the data once, up front ----
        TransactionStore store = new TransactionStore(CSV_FILE);

        try {
            // ---- Open the server socket ----
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("DVLA transactions service running.");
            System.out.println("Try: http://localhost:" + PORT + ROOT + "/transactions");

            // ---- The accept loop: handle clients forever ----
            while (true) {
                // accept() blocks (waits) here until a client connects.
                Socket clientSocket = serverSocket.accept();

                // Start a new thread to handle THIS client, then loop back
                // immediately to accept the next one. This is what lets the
                // server deal with many clients at the same time.
                RequestHandler handler = new RequestHandler(clientSocket, store, ROOT);
                handler.start();
            }
        } catch (IOException e) {
            System.out.println("Server could not start or stopped: " + e.getMessage());
        }
    }
}
