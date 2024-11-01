/*
 * Developer : Avishek Sapkot Sharma
 * Student ID : 3393440
 * Course : SENG4500
 * 
 * This program uses UDP based matchmaking algorithm to connect with others players over the local network
 * and TCP to communicate game messages between them.
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class Connect4 {
    // private variables that are permament
    private static final int ROWS = 6;
    private static final int COLS = 7;
    private static final char EMPTY = '.';
    private static final char PLAYER1 = 'X';
    private static final char PLAYER2 = 'O';

    // private variabels that are altered throughtout the program
    private boolean connected;
    private char[][] board;
    private boolean isPlayer1;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // constructor for the program, board is initialized at this stage
    public Connect4(){
        board = new char[ROWS][COLS];
        for (char[] row : board){
            Arrays.fill(row, EMPTY);
        }
    }

    public static void main(String[] args){
        if (args.length != 2) {
            System.out.println("Usage: java Connect4 <BROADCAST_ADDRESS> <BROADCAST_PORT>");
            return;
        }

        String broadcastAddress = args[0];
        int broadcastPort = Integer.parseInt(args[1]);
        // range for broadcast port
        if (broadcastPort < 1024 || broadcastPort > 65535){
            throw new IllegalArgumentException("Port number must be in the range of 1024 and 65535!");
        }
        Connect4 game = new Connect4();
        game.start(broadcastAddress, broadcastPort);
    }

    private void start(String broadcastAddress, int broadcastPort){
        // if connections is established, game begins
        try {
            if (findOpponent(broadcastAddress, broadcastPort)){
                playGame();
            }
        } 
        catch (IOException e) { // input output exception
            e.printStackTrace();
        }
    }

    // udp mathch making algorithm for connecting two players
    private boolean findOpponent(String broadcastAddress, int broadcastPort) throws IOException{
        DatagramSocket udpSocket = new DatagramSocket(null);
        // enabling multiple sockets to bind to the same address
        udpSocket.setReuseAddress(true);
        udpSocket.bind(new InetSocketAddress(broadcastAddress, broadcastPort));
        // socket time out is 30 seconds, after which current current client can send packets
        udpSocket.setSoTimeout(30000);

        InetAddress broadcastAddr = InetAddress.getByName(broadcastAddress);
        Random random = new Random();
        // generate random port in range 9000 to 9100
        int tcpPort = random.nextInt(100) + 9000;
        ServerSocket serverSocket = new ServerSocket(tcpPort);

        String message = "NEW GAME : " + tcpPort;
        byte[] sendData = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcastAddr, broadcastPort);

        System.out.println("Searching for an opponent...");

        connected = false;
        while (!connected){
            try {
                // receiving a response
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                udpSocket.setBroadcast(false);
                udpSocket.receive(receivePacket);

                // process received message
                String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
                String[] parts = receivedMessage.split(" : ");
                int opponentPort = Integer.parseInt(parts[1]);
                if (receivedMessage.startsWith("NEW GAME") && tcpPort != opponentPort){
                    // connect to the opponent on their port
                    socket = new Socket("localhost", opponentPort);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out = new PrintWriter(socket.getOutputStream(), true);
                    // send connected to status to opponent
                    out.println("Connected");
                    isPlayer1 = false;
                    System.out.println("Connected to opponent on port " + tcpPort + ". You are Player 2(O).");
                    udpSocket.close();
                    serverSocket.close();
                    return true;
                }
            } 
            catch (SocketTimeoutException e){
                // timeout occurred, send "NEW GAME" message as player 1
                udpSocket.setBroadcast(true);
                udpSocket.send(sendPacket);
                System.out.println("Sent message: " + message);

                // listening on tcp port for any incoming connections
                System.out.println("Listening on TCP port...");
                serverSocket.setSoTimeout(30000);
                try {
                    // if other client (player 2), receives NEW GAME and returns with successful connnections tatus
                    socket = serverSocket.accept();
                    BufferedReader localIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String receivedMessage = localIn.readLine();
                    if (receivedMessage.startsWith("Connected")){
                        out = new PrintWriter(socket.getOutputStream(), true);
                        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        isPlayer1 = true;
                        udpSocket.close();
                        serverSocket.close();
                        System.out.println("Connected to opponent on port " + tcpPort + ". You are Player 1(X).");
                        return true;
                    }
                } 
                catch (SocketTimeoutException timedOut){
                    System.out.println("No connection received. Listening on TCP port...");
                }
                
            }
        }
        // return false, because connection not established
        return false;
    }

    // method to simulate game
    private void playGame() throws IOException{
        Scanner scanner = new Scanner(System.in);
        boolean gameOver = false;

        // while game is not over, loop
        while (!gameOver){
            // calls the printer method
            printBoard();

            // checking which players turn it is, and if the current client is that player
            if ((isPlayer1 && isPlayer1Turn()) || (!isPlayer1 && !isPlayer1Turn())){
                System.out.print("Enter column (1-7): ");
                //String input = scanner.nextLine();
                try{
                    int column = scanner.nextInt();
                    if (isValidMove(column-1)){
                        // if move is valid, processes it into the board, and sends tcp message to opponent
                        makeMove(column-1);
                        out.println("INSERT : " + column);
                        // game is checked after each move
                        if (checkWin()){
                            out.println("YOU WIN");
                            System.out.println("You win!");
                            gameOver = true;
                        }
                    } 
                    else{
                        System.out.println("Invalid move. Try again.");
                    }
                }
                catch(InputMismatchException inputMismatchException){
                    out.println("ERROR");
                    System.exit(0);
                }
            }
            else{
                System.out.println("Waiting for opponent's move...");
    
                String response = in.readLine();
                // response must start with INSERT to place their next token
                if (response.startsWith("INSERT")){
                    int column = Integer.parseInt(response.split(" : ")[1]);
                    makeMove(column-1);
                    if (checkWin()){
                        System.out.println("You lose!");
                        gameOver = true;
                    }
                } 
                else if (response.equals("YOU WIN")){
                    System.out.println("You lose!");
                    gameOver = true;
                }
                else if (response.equals("ERROR")){
                    System.exit(0);
                }
            }
        }

        printBoard();
        scanner.close();
        socket.close();
    }

    // method to check if it is player 1s turn; counts token in the board
    private boolean isPlayer1Turn(){
        int totalMoves = 0;
        for (char[] row : board){
            for (char cell : row){
                if (cell != EMPTY){
                    totalMoves++;
                }
            }
        }
        return totalMoves % 2 == 0;
    }

    // method to check if the move is valid
    private boolean isValidMove(int column){
        return column >= 0 && column < COLS && board[0][column] == EMPTY;
    }

    // method to add the players token to the board
    private void makeMove(int column){
        for (int row = ROWS - 1; row >= 0; row--){
            if (board[row][column] == EMPTY){
                board[row][column] = isPlayer1Turn() ? PLAYER1 : PLAYER2;
                break;
            }
        }
    }

    // method to check if the game is over
    private boolean checkWin(){
        // check horizontal tokens
        for (int row = 0; row < ROWS; row++){
            for (int col = 0; col <= COLS - 4; col++){
                if (board[row][col] != EMPTY && board[row][col] == board[row][col+1] &&
                    board[row][col] == board[row][col+2] && board[row][col] == board[row][col+3]) {
                    return true;
                }
            }
        }

        // check vertical tokens
        for (int row = 0; row <= ROWS - 4; row++){
            for (int col = 0; col < COLS; col++){
                if (board[row][col] != EMPTY && board[row][col] == board[row+1][col] &&
                    board[row][col] == board[row+2][col] && board[row][col] == board[row+3][col]){
                    return true;
                }
            }
        }

        // check diagonal tokens (top-left to bottom-right)
        for (int row = 0; row <= ROWS - 4; row++){
            for (int col = 0; col <= COLS - 4; col++){
                if (board[row][col] != EMPTY && board[row][col] == board[row+1][col+1] &&
                    board[row][col] == board[row+2][col+2] && board[row][col] == board[row+3][col+3]){
                    return true;
                }
            }
        }

        // check diagonal tokens (bottom-left to top-right)
        for (int row = 3; row < ROWS; row++){
            for (int col = 0; col <= COLS - 4; col++){
                if (board[row][col] != EMPTY && board[row][col] == board[row-1][col+1] &&
                    board[row][col] == board[row-2][col+2] && board[row][col] == board[row-3][col+3]){
                    return true;
                }
            }
        }
        return false;
    }

    // method to print board to the cli
    private void printBoard(){
        System.out.println("----".repeat(5));
        System.out.println("1 2 3 4 5 6 7");
        for (char[] row : board){
            for (char cell : row){
                System.out.print(cell + " ");
            }
            System.out.println();
        }
        System.out.println("----".repeat(5));
        System.out.println();
    }
}