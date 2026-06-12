package com.mycompany.webdeliveryservices;
 
import java.sql.Connection;

import java.sql.DriverManager;

import java.sql.SQLException;
 
public class DBConnect {
 
    // Modifica questi tre valori con i tuoi dati MySQL

    private static final String URL = "jdbc:mysql://localhost:3306/webdelivery";

    private static final String USER = "root";

    private static final String PASSWORD = "pass123";
 
    public static Connection getConnection() throws SQLException {

        return DriverManager.getConnection(URL, USER, PASSWORD);

    }

}
 