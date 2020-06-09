package com.company;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Formatter;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
    To run the Client and Server application, we compile both of them.
    Then first run the server application and then run the Client application
 */

public class TicTacToeServer extends JFrame {
    private ServerSocket server;
    private final String[] board;
    private String turn;
    private final ExecutorService runGame;
    private final Lock gameLock;
    private final Condition otherPlayerConnected;
    private final Condition otherPlayerTurn;
    private final JTextArea outputArea;
    private final Player[] players;
    private int currentPlayer;

    public TicTacToeServer() {
        super("Tic-Tac-Toe Server");
        board = new String[9];
        for (int a = 0; a < 9; a++) {
            board[a] = "";
        }

        runGame = Executors.newFixedThreadPool(2);
        gameLock = new ReentrantLock();

        // control players connection and turns
        otherPlayerConnected = gameLock.newCondition();
        otherPlayerTurn = gameLock.newCondition();

        turn = "X";

        // start server and wait for a connection
        try {
            server = new ServerSocket(55555, 2);
        } catch (IOException ioException) {
            System.out.println(ioException.toString());
            System.exit(1);
        }

        // create JTextArea for output
        outputArea = new JTextArea();
        add(outputArea, BorderLayout.CENTER);
        outputArea.setText("Server is waiting for connections\n");
        setSize(300, 300);
        setVisible(true);

        players = new Player[2];
        currentPlayer = 0;
    }

    public void play() {
        // waiting for each client to connect
        for (int i = 0; i < players.length; i++) {
            try {
                //Player objects are created after client's connection
                //we manage the connection as a separate thread, and the thread is executed in the runGame thread pool
                players[i] = new Player(server.accept(), i);
                runGame.execute(players[i]);
            } catch (IOException ioException) {
                System.out.println(ioException.toString());
                System.exit(1);
            }
        }

        // lock game to signal player X's thread
        gameLock.lock();

        try {
            //so player X can play now
            players[0].setSuspended(false);
            // waking up player X's thread
            otherPlayerConnected.signal();
        } finally {
            gameLock.unlock();
        }
    }

    // private inner class Player manages each Player as a runnable
    private class Player implements Runnable {

        private final Socket connection;
        private Scanner input;
        private Formatter output;
        private final int playerNumber;
        private final String symbol;
        private boolean suspended;

        // set up Player thread
        public Player(Socket socket, int number) {
            suspended = true;
            playerNumber = number;
            if (number == 0)
                symbol = "X";
            else
                symbol = "O";

            // storing socket for client
            connection = socket;

            // obtaining streams from Socket
            try {
                input = new Scanner(connection.getInputStream());
                output = new Formatter(connection.getOutputStream());
            } catch (IOException ioException) {
                System.out.println(ioException.toString());
                System.exit(1);
            }
        }

        // set whether thread is suspended
        public void setSuspended(boolean status) {
            suspended = status;
        }

        public void opponentMove(int location) {
            output.format("Opponent moved\n");
            output.format("%d\n", location);
            output.flush();
            output.format(hasWinner() ? "DEFEAT\n" : boardFilledUp() ? "DRAW\n" : "");
            output.flush();
        }

        // thread begins its life inside run() method
        @Override
        public void run() {
            try {
                display("Player " + symbol + " connected\n");
                output.format("%s\n", symbol);
                output.flush();

                // we're waiting until both players are connected
                if (playerNumber == 0) {
                    output.format("%s\n%s", "Player X connected", "Waiting for another player\n");
                    output.flush();
                    // locking game to wait for another player
                    gameLock.lock();

                    try {
                        while (suspended) {
                            otherPlayerConnected.await();
                        }
                    } catch (InterruptedException exception) {
                        System.out.println(exception.toString());
                    } finally {
                        gameLock.unlock();
                    }

                    output.format("Other player connected. Your move.\n");
                    output.flush();
                } else {
                    output.format("Player O connected, please wait\n");
                    output.flush();
                }

                while (!isGameOver()) {
                    int location = 0;

                    if (input.hasNext()) {
                        location = input.nextInt();
                    }
                    if (makeMove(location, playerNumber)) {
                        display("\nlocation: " + location);
                        output.format("Valid move.\n");
                        output.flush();
                        output.format(hasWinner() ? "VICTORY\n" : boardFilledUp() ? "DRAW\n" : "");
                        output.flush();
                    } else {
                        output.format("Invalid move, try again\n");
                        output.flush();
                    }
                }
            } finally {
                try {
                    // close connection to the client
                    connection.close();
                } catch (IOException ioException) {
                    System.out.println(ioException.toString());
                    System.exit(1);
                }
            }
        }
    }

    public boolean hasWinner() {
        return (!board[0].isEmpty() && board[0].equals(board[1]) && board[0].equals(board[2]))
                || (!board[3].isEmpty() && board[3].equals(board[4]) && board[3].equals(board[5]))
                || (!board[6].isEmpty() && board[6].equals(board[7]) && board[6].equals(board[8]))
                || (!board[0].isEmpty() && board[0].equals(board[3]) && board[0].equals(board[6]))
                || (!board[1].isEmpty() && board[1].equals(board[4]) && board[1].equals(board[7]))
                || (!board[2].isEmpty() && board[2].equals(board[5]) && board[2].equals(board[8]))
                || (!board[0].isEmpty() && board[0].equals(board[4]) && board[0].equals(board[8]))
                || (!board[2].isEmpty() && board[2].equals(board[4]) && board[2].equals(board[6]));
    }

    public boolean boardFilledUp() {
        for (int i = 0; i < board.length; ++i) {
            if (board[i].isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public boolean isGameOver() {
        return hasWinner() || boardFilledUp();
    }

    public boolean makeMove(int location, int player) {
        while (player != currentPlayer) {
            // lock game to wait for other player to make a move
            gameLock.lock();

            try {
                // wait for player's turn
                otherPlayerTurn.await();
            } catch (InterruptedException exception) {
                System.out.println(exception.toString());
            } finally {
                // unlock game after waiting
                gameLock.unlock();
            }
        }

        if (!isSlotTaken(location)) {
            board[location] = turn;
            if (turn.equals("X")) {
                turn = "Y";
                currentPlayer = 1;
            }
            else {
                turn = "X";
                currentPlayer = 0;
            }
            players[currentPlayer].opponentMove(location);

            // lock game to wait for other player to make a move
            gameLock.lock();

            try {
                otherPlayerTurn.signal();
            } finally {
                gameLock.unlock();
            }

            return true;
        } else {
            return false;
        }
    }

    private void display(String message) {
        // display message from event-dispatch thread of execution
        SwingUtilities.invokeLater(() -> {
            outputArea.append(message);
        });
    }

    public boolean isSlotTaken(int location) {
        return board[location].equals("X") || board[location].equals("O");
    }
}