package com.mycompany.webdeliveryservices;
 
import jakarta.ws.rs.*;

import jakarta.ws.rs.core.*;

import java.sql.*;

import java.util.*;
 
@Path("/auth")

@Produces(MediaType.APPLICATION_JSON)

@Consumes(MediaType.APPLICATION_JSON)

public class AuthResource {
 
   

    @POST

    @Path("/login")

    public Response login(Map<String, String> credentials) {
 
        String email = credentials.get("email");

        String password = credentials.get("password");
 
        if (email == null || password == null) {

            return Response.status(Response.Status.BAD_REQUEST)

                           .entity(Map.of("error", "Email e password obbligatori"))

                           .build();

        }
 
        try (Connection conn = DBConnect.getConnection()) {
 
            // Verifica utente nel DB

            String sql = "SELECT id_utente, nome_completo, ruolo " +

                         "FROM Utente WHERE email = ? AND password = ?";

            PreparedStatement ps = conn.prepareStatement(sql);

            ps.setString(1, email);

            ps.setString(2, password);

            ResultSet rs = ps.executeQuery();
 
            if (!rs.next()) {

                return Response.status(Response.Status.UNAUTHORIZED)

                               .entity(Map.of("error", "Credenziali non valide"))

                               .build();

            }
 
            int idUtente = rs.getInt("id_utente");

            String nomeCompleto = rs.getString("nome_completo");

            String ruolo = rs.getString("ruolo");
 
            // Genera token univoco

            String token = UUID.randomUUID().toString();
 
            // Salva il token nel DB

            String sqlInsert = "INSERT INTO Sessione (token, id_utente) VALUES (?, ?)";

            PreparedStatement psInsert = conn.prepareStatement(sqlInsert);

            psInsert.setString(1, token);

            psInsert.setInt(2, idUtente);

            psInsert.executeUpdate();
 
            // Restituisce token + info utente

            Map<String, Object> response = new HashMap<>();

            response.put("token", token);

            response.put("id_utente", idUtente);

            response.put("nome_completo", nomeCompleto);

            response.put("ruolo", ruolo);
 
            return Response.ok(response).build();
 
        } catch (SQLException e) {

            return Response.serverError()

                           .entity(Map.of("error", e.getMessage()))

                           .build();

        }

    }
 
    // OP.1b — DELETE /api/auth/logout

    @DELETE

    @Path("/logout")

    public Response logout(@HeaderParam("Authorization") String token) {
 
        if (token == null || token.isEmpty()) {

            return Response.status(Response.Status.BAD_REQUEST)

                           .entity(Map.of("error", "Token mancante"))

                           .build();

        }
 
        try (Connection conn = DBConnect.getConnection()) {
 
            String sql = "DELETE FROM Sessione WHERE token = ?";

            PreparedStatement ps = conn.prepareStatement(sql);

            ps.setString(1, token);

            int rows = ps.executeUpdate();
 
            if (rows == 0) {

                return Response.status(Response.Status.NOT_FOUND)

                               .entity(Map.of("error", "Token non trovato"))

                               .build();

            }
 
            return Response.noContent().build(); // 204 = logout OK
 
        } catch (SQLException e) {

            return Response.serverError()

                           .entity(Map.of("error", e.getMessage()))

                           .build();

        }

    }

}
 