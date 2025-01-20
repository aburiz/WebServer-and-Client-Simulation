package ca.concordia.server;
//Abdul Rahman Anver Mohamed Rizan - 40272256
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

// The WebServer class handles client connections and transfers between accounts.
public class WebServer {

    // Server configuration constants
    private static final int PORT = 5000; // Port for the server to listen on
    private static final int THREAD_POOL_SIZE = 10; // Number of worker threads in the pool

    // Concurrent data structures and utilities
    private static final ConcurrentHashMap<Integer, Account> accounts = new ConcurrentHashMap<>();
    private static final ReentrantLock transferLock = new ReentrantLock(); // Lock for safe transfer operations
    private static final Semaphore fileSemaphore = new Semaphore(1); // Semaphore for file access synchronization
    private static final String ACCOUNTS_FILE = "src\\main\\resources\\accounts.txt"; // Path to accounts file
    private static final Logger logger = Logger.getLogger(WebServer.class.getName()); // Logger for logging activities

    public static void main(String[] args) {
        // Initialize accounts from the specified file
        initializeAccounts(ACCOUNTS_FILE);

        WebServer server = new WebServer();
        try {
            server.start(); // Start the server
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server failed to start", e);
        }
    }

    // Load account data from a file into the concurrent hash map
    private static void initializeAccounts(String fileName) {
        try {
            fileSemaphore.acquire(); // Ensure exclusive access to the file
            try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        int id = Integer.parseInt(parts[0].trim());
                        int balance = Integer.parseInt(parts[1].trim());
                        accounts.put(id, new Account(balance, id)); // Populate the accounts map
                    }
                }
                logger.info("Accounts initialized from file: " + fileName);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to initialize accounts", e);
            } finally {
                fileSemaphore.release(); // Release file semaphore
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupt status
            logger.log(Level.SEVERE, "Failed to acquire file semaphore", e);
        }
    }

    // Save updated account data back to the file
    private static void updateAccountsFile(String fileName) {
        try {
            fileSemaphore.acquire(); // Ensure exclusive access to the file
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))) {
                for (Integer id : accounts.keySet()) {
                    Account account = accounts.get(id);
                    bw.write(id + "," + account.getBalance());
                    bw.newLine();
                }
                logger.info("Accounts file updated: " + fileName);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to update accounts file", e);
            } finally {
                fileSemaphore.release(); // Release file semaphore
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "Failed to acquire file semaphore", e);
        }
    }

    // Start the server and accept client connections
    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT); // Create server socket
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE); // Create thread pool

        logger.info("Server started on port " + PORT);
        logger.info("Waiting for a client to connect...");

        try {
            while (true) {
                // Accept incoming client connections
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connected");
                threadPool.execute(new ClientHandler(clientSocket)); // Handle client using a thread from the pool
            }
        } finally {
            serverSocket.close(); // Close the server socket when shutting down
        }
    }

    // Inner class to handle client requests
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        // Constructor to initialize with the client socket
        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream()) {

                // Read the client's request
                String request = in.readLine();
                if (request != null) {
                    if (request.startsWith("GET")) {
                        handleGetRequest(out); // Handle GET request
                    } else if (request.startsWith("POST")) {
                        handlePostRequest(in, out); // Handle POST request
                    }
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Client handler failed", e);
            } finally {
                try {
                    clientSocket.close(); // Ensure the client socket is closed
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Failed to close client socket", e);
                }
            }
        }

        // Handle GET request by responding with a simple HTML form
        private void handleGetRequest(OutputStream out) throws IOException {
            logger.info("Handling GET request");
            String response = """
                    HTTP/1.1 200 OK\r\n\r\n
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <title>Concordia Transfers</title>
                    </head>
                    <body>
                    <h1>Welcome to Concordia Transfers</h1>
                    <p>Select the account and amount to transfer</p>
                    <form action="/submit" method="post">
                            <label for="account">Account:</label>
                            <input type="text" id="account" name="account"><br><br>
                            <label for="value">Value:</label>
                            <input type="text" id="value" name="value"><br><br>
                            <label for="toAccount">To Account:</label>
                            <input type="text" id="toAccount" name="toAccount"><br><br>
                            <label for="toValue">To Value:</label>
                            <input type="text" id="toValue" name="toValue"><br><br>
                            <input type="submit" value="Submit">
                        </form>
                    </body>
                    </html>
                    """;
            out.write(response.getBytes());
            out.flush();
        }

        // Handle POST request to process transfers
        private void handlePostRequest(BufferedReader in, OutputStream out) throws IOException {
            logger.info("Handling POST request");
            StringBuilder requestBody = new StringBuilder();
            int contentLength = 0;
            String line;

            // Read headers to determine content length
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length")) {
                    contentLength = Integer.parseInt(line.substring(line.indexOf(' ') + 1));
                }
            }

            // Read the request body based on content length
            for (int i = 0; i < contentLength; i++) {
                requestBody.append((char) in.read());
            }

            logger.info(requestBody.toString());

            // Parse form inputs and process the transfer
            String[] params = requestBody.toString().split("&");
            String account = null, value = null, toAccount = null, toValue = null;

            for (String param : params) {
                String[] parts = param.split("=");
                if (parts.length == 2) {
                    String key = URLDecoder.decode(parts[0], "UTF-8");
                    String val = URLDecoder.decode(parts[1], "UTF-8");

                    switch (key) {
                        case "account" -> account = val;
                        case "value" -> value = val;
                        case "toAccount" -> toAccount = val;
                        case "toValue" -> toValue = val;
                    }
                }
            }

            // Process the transfer and create the response
            String resultMessage = processTransfer(Integer.parseInt(account), Integer.parseInt(toAccount), Integer.parseInt(value), Integer.parseInt(toValue));
            String responseContent = """
                    <html><body><h1>Thank you for using Concordia Transfers</h1>
                    <h2>Transfer Result:</h2>
                    <p>%s</p>
                    <h2>Received Form Inputs:</h2>
                    <p>Account: %s</p>
                    <p>Value: %s</p>
                    <p>To Account: %s</p>
                    <p>To Value: %s</p>
                    </body></html>
                    """.formatted(resultMessage, account, value, toAccount, toValue);

            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Length: " + responseContent.length() + "\r\n" +
                    "Content-Type: text/html\r\n\r\n" +
                    responseContent;

            out.write(response.getBytes());
            out.flush();
        }

        // Method to process fund transfer between accounts
        private String processTransfer(int fromAccount, int toAccount, int value, int toValue) {
            
         // Check for negative numbers
            if (value < 0 || toValue < 0) {
                return "Transfer failed: Negative values are not allowed.";
            }

         // Check for invalid input (non-numeric values)
            try {
            Integer.parseInt(String.valueOf(fromAccount));
            Integer.parseInt(String.valueOf(toAccount));
            Integer.parseInt(String.valueOf(value));
            Integer.parseInt(String.valueOf(toValue));
            } catch (NumberFormatException e) {
                return "Transfer failed: Invalid input. Please enter numeric values.";
            }
            Account sourceAccount = accounts.get(fromAccount);
            Account destinationAccount = accounts.get(toAccount);

            // Validate account existence and transfer parameters
            if (sourceAccount == null) {
                return "Transfer failed: Source account does not exist.";
            }
            if (destinationAccount == null) {
                return "Transfer failed: Destination account does not exist.";
            }
            if (sourceAccount.equals(destinationAccount)) {
                return "Transfer failed: Cannot transfer funds to the same account.";
            }
            if (value != toValue) {
                return "Transfer failed: Value and To Value must be the same.";
            }
            // Check for zero values
            if (value == 0 || toValue == 0) {
                return "Transfer failed: Transfer amount must be greater than zero.";
            }
            if (sourceAccount.getBalance() < value) {
                return "Transfer failed: Insufficient funds in source account.";
            }

            // Perform the transfer in a thread-safe manner
            transferLock.lock();
            try {
                sourceAccount.withdraw(value);
                destinationAccount.deposit(value);
                logger.info("Transferred " + value + " from account " + fromAccount + " to account " + toAccount);
                updateAccountsFile(ACCOUNTS_FILE); // Update the file after a successful transfer
            } finally {
                transferLock.unlock(); // Ensure the lock is released
            }
            return "Transfer successful: " + value + " transferred from account " + fromAccount + " to account " + toAccount + ".";
        }
    }
}
