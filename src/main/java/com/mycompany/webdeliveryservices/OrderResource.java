package com.mycompany.webdeliveryservices;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.sql.*;
import java.util.*;

@Path("/ordini")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    // PUNTO 8: Lista degli ordini filtrati per data e stato (Solo Staff/Admin)
    @GET
    public Response getOrdiniFiltrati(
            @QueryParam("data") String data,
            @QueryParam("stato") String stato,
            @HeaderParam("Authorization") String token) {
        
        Map<String, Object> user = AuthHelper.getUserFromToken(token);
        if (user == null || (!user.get("ruolo").equals("admin") && !user.get("ruolo").equals("staff"))) {
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "Accesso negato: Solo Staff o Admin")).build();
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
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            
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

    // PUNTO 9: Estrazione ordini effettuati da un determinato utente
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

    // PUNTO 5: Inserimento prodotto con caratteristiche
    @POST
    @Path("/{id}/prodotti")
    public Response aggiungiProdottoAOrdine(
            @PathParam("id") int idOrdine,
            Map<String, Object> payload,
            @HeaderParam("Authorization") String token) {
        
        if (AuthHelper.getUserFromToken(token) == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        try (Connection conn = DBConnect.getConnection()) {
            conn.setAutoCommit(false); // Inizio transazione per sicurezza

            int idProdotto = (int) payload.get("id_prodotto");
            int quantita = (int) payload.get("quantita");
            double prezzoUnitario = Double.parseDouble(payload.get("prezzo_unitario").toString());

            // Inserimento prodotto base
            String sql = "INSERT INTO Dettaglio_Ordine (id_ordine, id_prodotto, quantita, prezzo_unitario) VALUES (?, ?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idOrdine);
            ps.setInt(2, idProdotto);
            ps.setInt(3, quantita);
            ps.setDouble(4, prezzoUnitario);
            ps.executeUpdate();

            // Inserimento caratteristiche (PUNTO 5)
            if (payload.containsKey("caratteristiche")) {
                List<Integer> caratteristiche = (List<Integer>) payload.get("caratteristiche");
                if (caratteristiche != null && !caratteristiche.isEmpty()) {
                    String sqlCarat = "INSERT INTO Dettaglio_Ordine_Caratteristica (id_ordine, id_prodotto, id_caratteristica) VALUES (?, ?, ?)";
                    PreparedStatement psCarat = conn.prepareStatement(sqlCarat);
                    for (Integer idCarat : caratteristiche) {
                        psCarat.setInt(1, idOrdine);
                        psCarat.setInt(2, idProdotto);
                        psCarat.setInt(3, idCarat);
                        psCarat.addBatch();
                    }
                    psCarat.executeBatch();
                }
            }

            conn.commit(); 
            return Response.status(Response.Status.CREATED).entity(Map.of("message", "Prodotto aggiunto all'ordine con successo")).build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    // PUNTO 13: Lista prodotti dell'ordine inclusi di caratteristiche
    @GET
    @Path("/{id}/prodotti")
    public Response getProdottiOrdine(@PathParam("id") int idOrdine, @HeaderParam("Authorization") String token) {
        if (AuthHelper.getUserFromToken(token) == null) return Response.status(Response.Status.UNAUTHORIZED).build();

        List<Map<String, Object>> prodotti = new ArrayList<>();
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "SELECT d.id_prodotto, p.nome, d.quantita, d.prezzo_unitario FROM Dettaglio_Ordine d JOIN Prodotto p ON d.id_prodotto = p.id_prodotto WHERE d.id_ordine = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idOrdine);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                Map<String, Object> p = new HashMap<>();
                int idProd = rs.getInt("id_prodotto");
                p.put("id_prodotto", idProd);
                p.put("nome", rs.getString("nome"));
                p.put("quantita", rs.getInt("quantita"));
                p.put("prezzo_unitario", rs.getDouble("prezzo_unitario"));

                // Recupero caratteristiche specifiche per questo prodotto nell'ordine
                List<Map<String, Object>> caratteristiche = new ArrayList<>();
                String sqlCarat = "SELECT c.id_caratteristica, c.nome FROM Dettaglio_Ordine_Caratteristica doc JOIN Caratteristica c ON doc.id_caratteristica = c.id_caratteristica WHERE doc.id_ordine = ? AND doc.id_prodotto = ?";
                PreparedStatement psCarat = conn.prepareStatement(sqlCarat);
                psCarat.setInt(1, idOrdine);
                psCarat.setInt(2, idProd);
                ResultSet rsCarat = psCarat.executeQuery();
                while (rsCarat.next()) {
                    caratteristiche.add(Map.of("id_caratteristica", rsCarat.getInt("id_caratteristica"), "nome", rsCarat.getString("nome")));
                }
                p.put("caratteristiche", caratteristiche);
                prodotti.add(p);
            }
            return Response.ok(prodotti).build();
        } catch (SQLException e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    // PUNTO 6: Tempo stimato e Prezzo totale
    @GET
    @Path("/{id}/tempo-consegna")
    public Response getTempoConsegna(@PathParam("id") int idOrdine, @HeaderParam("Authorization") String token) {
        if (AuthHelper.getUserFromToken(token) == null) return Response.status(Response.Status.UNAUTHORIZED).build();
        return Response.ok(Map.of("id_ordine", idOrdine, "tempo_stimato_minuti", 45, "messaggio", "In preparazione")).build();
    }

    @GET
    @Path("/{id}/prezzo-totale")
    public Response getPrezzoTotale(@PathParam("id") int idOrdine, @HeaderParam("Authorization") String token) {
        if (AuthHelper.getUserFromToken(token) == null) return Response.status(Response.Status.UNAUTHORIZED).build();
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "SELECT prezzo_totale FROM Ordine WHERE id_ordine = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idOrdine);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Response.ok(Map.of("id_ordine", idOrdine, "prezzo_totale", rs.getDouble("prezzo_totale"))).build();
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) { return Response.serverError().build(); }
    }

    // PUNTO 7: Aggiornamento stato 
    @PUT
    @Path("/{id}/stato")
    public Response aggiornaStato(@PathParam("id") int idOrdine, Map<String, String> payload, @HeaderParam("Authorization") String token) {
        Map<String, Object> user = AuthHelper.getUserFromToken(token);
        if (user == null || (!user.get("ruolo").equals("admin") && !user.get("ruolo").equals("staff"))) {
            return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "Solo lo staff può aggiornare lo stato")).build();
        }
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "UPDATE Ordine SET stato_attuale = ? WHERE id_ordine = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, payload.get("stato"));
            ps.setInt(2, idOrdine);
            if (ps.executeUpdate() > 0) return Response.ok(Map.of("message", "Stato aggiornato")).build();
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (SQLException e) { return Response.serverError().build(); }
    }

    // PUNTO 11: Lista operatori coinvolti
    @GET
    @Path("/{id}/operatori")
    public Response getOperatoriOrdine(@PathParam("id") int idOrdine, @HeaderParam("Authorization") String token) {
        Map<String, Object> user = AuthHelper.getUserFromToken(token);
        if (user == null || (!user.get("ruolo").equals("admin") && !user.get("ruolo").equals("staff"))) return Response.status(Response.Status.FORBIDDEN).build();
        
        List<Map<String, String>> ops = new ArrayList<>();
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "SELECT DISTINCT u.nome_completo, u.ruolo FROM Storico_Stati_Ordine s JOIN Utente u ON s.id_personale = u.id_utente WHERE s.id_ordine = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, idOrdine);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ops.add(Map.of("nome", rs.getString("nome_completo"), "ruolo", rs.getString("ruolo")));
            return Response.ok(ops).build();
        } catch (SQLException e) { return Response.serverError().build(); }
    }

    // PUNTO 12: Annullamento ordine
   @DELETE
@Path("/{prodId}/features/{featId}")
public Response deleteFeature(
        @PathParam("prodId") int prodId,
        @PathParam("featId") int featId,
        @HeaderParam("Authorization") String token) { 

    Map<String, Object> user = AuthHelper.getUserFromToken(token);
    if (user == null || (!user.get("ruolo").equals("admin") && !user.get("ruolo").equals("staff"))) {
        return Response.status(Response.Status.FORBIDDEN)
                       .entity(Map.of("error", "Permesso negato"))
                       .build();
    }
   { return Response.serverError().build(); }
    }

   
    @GET
    @Path("/statistiche")
    public Response getStatistiche(@HeaderParam("Authorization") String token) {
        Map<String, Object> user = AuthHelper.getUserFromToken(token);
        if (user == null || !user.get("ruolo").equals("admin")) return Response.status(Response.Status.FORBIDDEN).entity(Map.of("error", "Solo ADMIN")).build();
        
        try (Connection conn = DBConnect.getConnection()) {
            String sql = "SELECT COUNT(id_ordine) AS tot, SUM(prezzo_totale) AS incasso FROM Ordine WHERE stato_attuale != 'annullato'";
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Response.ok(Map.of("totale_ordini", rs.getInt("tot"), "incasso_totale", rs.getDouble("incasso"))).build();
        } catch (SQLException e) { return Response.serverError().build(); }
        return Response.serverError().build();
    }
}