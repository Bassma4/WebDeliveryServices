package com.mycompany.webdeliveryservices;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.sql.*;
import java.util.*;

@Path("/ordini")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    
    // Lista degli ordini filtrati per data e stato corrente 
    @GET
    public Response getOrdiniFiltrati(
            @QueryParam("data") String data,
            @QueryParam("stato") String stato,
            @HeaderParam("Authorization") String token) {
        
        Map<String, Object> user = AuthHelper.getUserFromToken(token);
        if (user == null || (!user.get("ruolo").equals("admin") && !user.get("ruolo").equals("staff"))) {
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "Accesso negato")).build();
        }

        List<Map<String, Object>> risultati = new ArrayList<>();
        try (Connection conn = DBConnect.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT * FROM Ordine WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (data != null && !data.isEmpty()) {
                sql.append(" AND DATE(data_creazione) = ?");
                params.add(data);
            }
            if (stato != null && !stato.isEmpty()) {
                sql.append(" AND stato_attuale = ?");
                params.add(stato);
            }
            
            sql.append(" ORDER BY data_creazione DESC");

            PreparedStatement ps = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> o = new HashMap<>();
                o.put("id_ordine", rs.getInt("id_ordine"));
                o.put("data_creazione", rs.getString("data_creazione"));
                o.put("stato_attuale", rs.getString("stato_attuale"));
                o.put("prezzo_totale", rs.getDouble("prezzo_totale"));
                risultati.add(o);
            }
            return Response.ok(risultati).build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    
    // Estrazione ordini effettuati da un determinato utente 
    @GET
    @Path("/me")
    public Response getMieiOrdini(@HeaderParam("Authorization") String token) {
        Map<String, Object> user = AuthHelper.getUserFromToken(token);
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "Devi loggarti")).build();
        }

        int idUtente = (int) user.get("id_utente");
        List<Map<String, Object>> ordini = new ArrayList<>();

        try (Connection conn = DBConnect.getConnection()) {
            String sql = "SELECT id_ordine, data_creazione, prezzo_totale, stato_attuale FROM Ordine WHERE id_cliente = ? ORDER BY data_creazione DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idUtente);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> ordine = new HashMap<>();
                ordine.put("id_ordine", rs.getInt("id_ordine"));
                ordine.put("data_creazione", rs.getString("data_creazione"));
                ordine.put("prezzo_totale", rs.getDouble("prezzo_totale"));
                ordine.put("stato_attuale", rs.getString("stato_attuale"));
                ordini.add(ordine);
            }
            return Response.ok(ordini).build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

   
    // Inserimento di un prodotto in un ordine
    @POST
    @Path("/{id}/prodotti")
    public Response aggiungiProdottoAOrdine(
            @PathParam("id") int idOrdine,
            Map<String, Object> payload,
            @HeaderParam("Authorization") String token) {
        
        if (AuthHelper.getUserFromToken(token) == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        try (Connection conn = DBConnect.getConnection()) {
            String sql = "INSERT INTO Dettaglio_Ordine (id_ordine, id_prodotto, quantita, prezzo_unitario) VALUES (?, ?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idOrdine);
            ps.setInt(2, (int) payload.get("id_prodotto"));
            ps.setInt(3, (int) payload.get("quantita"));
            ps.setDouble(4, Double.parseDouble(payload.get("prezzo_unitario").toString()));
            ps.executeUpdate();

            return Response.status(Response.Status.CREATED).entity(Map.of("message", "Prodotto aggiunto all'ordine")).build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

   
    // Lista dei prodotti presenti in un ordine
    @GET
    @Path("/{id}/prodotti")
    public Response getProdottiOrdine(@PathParam("id") int idOrdine, @HeaderParam("Authorization") String token) {
        if (AuthHelper.getUserFromToken(token) == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        List<Map<String, Object>> prodotti = new ArrayList<>();
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "SELECT d.quantita, d.prezzo_unitario, p.nome FROM Dettaglio_Ordine d JOIN Prodotto p ON d.id_prodotto = p.id_prodotto WHERE d.id_ordine = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idOrdine);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> p = new HashMap<>();
                p.put("nome", rs.getString("nome"));
                p.put("quantita", rs.getInt("quantita"));
                p.put("prezzo_unitario", rs.getDouble("prezzo_unitario"));
                prodotti.add(p);
            }
            return Response.ok(prodotti).build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    
    // Estrazione del tempo stimato di consegna
    @GET
    @Path("/{id}/tempo-consegna")
    public Response getTempoConsegna(@PathParam("id") int idOrdine, @HeaderParam("Authorization") String token) {
        if (AuthHelper.getUserFromToken(token) == null) return Response.status(Response.Status.UNAUTHORIZED).build();
        
        
        return Response.ok(Map.of("id_ordine", idOrdine, "tempo_stimato_minuti", 45, "messaggio", "In preparazione")).build();
    }

    
    // Estrazione del prezzo totale di un ordine
    @GET
    @Path("/{id}/prezzo-totale")
    public Response getPrezzoTotale(@PathParam("id") int idOrdine, @HeaderParam("Authorization") String token) {
        if (AuthHelper.getUserFromToken(token) == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        try (Connection conn = DBConnect.getConnection()) {
            String sql = "SELECT prezzo_totale FROM Ordine WHERE id_ordine = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idOrdine);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Response.ok(Map.of("id_ordine", idOrdine, "prezzo_totale", rs.getDouble("prezzo_totale"))).build();
            }
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "Ordine non trovato")).build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    
    // Aggiornamento dello stato di un ordine
    @PUT
    @Path("/{id}/stato")
    public Response aggiornaStato(
            @PathParam("id") int idOrdine,
            Map<String, String> payload,
            @HeaderParam("Authorization") String token) {
        
        Map<String, Object> user = AuthHelper.getUserFromToken(token);
        if (user == null || (!user.get("ruolo").equals("admin") && !user.get("ruolo").equals("staff"))) {
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "Solo lo staff può aggiornare lo stato")).build();
        }

        String nuovoStato = payload.get("stato");
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "UPDATE Ordine SET stato_attuale = ? WHERE id_ordine = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, nuovoStato);
            ps.setInt(2, idOrdine);
            int rows = ps.executeUpdate();
            
            if (rows > 0) return Response.ok(Map.of("message", "Stato aggiornato a " + nuovoStato)).build();
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

   
    // Lista degli operatori coinvolti nella gestione di un ordine
    @GET
    @Path("/{id}/operatori")
    public Response getOperatoriOrdine(@PathParam("id") int idOrdine, @HeaderParam("Authorization") String token) {
        if (AuthHelper.getUserFromToken(token) == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        List<Map<String, String>> operatori = new ArrayList<>();
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "SELECT DISTINCT u.nome_completo, u.ruolo FROM Storico_Stati_Ordine s JOIN Utente u ON s.id_personale = u.id_utente WHERE s.id_ordine = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idOrdine);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                operatori.add(Map.of("nome", rs.getString("nome_completo"), "ruolo", rs.getString("ruolo")));
            }
            return Response.ok(operatori).build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    // Annullamento di un ordine
    @DELETE
    @Path("/{id}")
    public Response annullaOrdine(@PathParam("id") int idOrdine, @HeaderParam("Authorization") String token) {
        Map<String, Object> user = AuthHelper.getUserFromToken(token);
        if (user == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        try (Connection conn = DBConnect.getConnection()) {
            String sql = "UPDATE Ordine SET stato_attuale = 'annullato' WHERE id_ordine = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idOrdine);
            int rows = ps.executeUpdate();
            
            if (rows > 0) return Response.noContent().build(); 
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }
}