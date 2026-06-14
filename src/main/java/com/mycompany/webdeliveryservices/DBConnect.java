package com.mycompany.webdeliveryservices;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnect {

    private static final String URL = "jdbc:mysql://localhost:3306/webdelivery?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = "pass123";

    public static Connection getConnection() throws SQLException {
        try {
            
            Class.forName("com.mysql.cj.jdbc.Driver");
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver MySQL non trovato da Tomcat!", e);
        }
    }
}