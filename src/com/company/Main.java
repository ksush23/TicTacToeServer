package com.company;
import javax.swing.*;

public class Main{
    public static void main(String args[]){
        TicTacToeServer server = new TicTacToeServer();
        server.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        server.play();
    }
}
