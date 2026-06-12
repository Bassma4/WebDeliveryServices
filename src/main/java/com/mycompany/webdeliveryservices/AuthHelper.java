package com.mycompany.webdeliveryservices;
 
import java.sql.*;

import java.util.*;
 
public class AuthHelper {
 
    /**

     * Verifica il token e restituisce le info dell'utente.

     * Ritorna null se il token non è valido.

     */

    public static Map<String, Object> getUserFromToken(String token) {

        if (token == null || token.isEmpty()) return null;
 
        try (Connection conn = DBConnect.getConnection()) {
 
            String sql = "SELECT u.id_utente, u.ruolo, u.nome_completo " +

                         "FROM Sessione s JOIN Utente u ON s.id_utente = u.id_utente " +

                         "WHERE s.token = ?";

            PreparedStatement ps = conn.prepareStatement(sql);

            ps.setString(1, token);

            ResultSet rs = ps.executeQuery();
 
            if (rs.next()) {

                Map<String, Object> user = new HashMap<>();

                user.put("id_utente", rs.getInt("id_utente"));

                user.put("ruolo", rs.getString("ruolo"));

                user.put("nome_completo", rs.getString("nome_completo"));

                return user;

            }

            return null;
 
        } catch (SQLException e) {

            return null;

        }

    }

}
 