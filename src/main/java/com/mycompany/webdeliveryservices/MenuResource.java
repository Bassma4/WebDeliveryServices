package com.mycompany.webdeliveryservices;
 
import jakarta.ws.rs.*;

import jakarta.ws.rs.core.*;

import java.sql.*;

import java.util.*;
 
@Path("/menu")

@Produces(MediaType.APPLICATION_JSON)

public class MenuResource {
 
    
    @GET

    public Response getMenu() {

        List<Map<String, Object>> products = new ArrayList<>();

        try (Connection conn = DBConnect.getConnection()) {
 
          

            String sqlProd = "SELECT id_prodotto, nome, descrizione, prezzo_base FROM Prodotto";

            PreparedStatement ps = conn.prepareStatement(sqlProd);

            ResultSet rs = ps.executeQuery();
 
            while (rs.next()) {

                Map<String, Object> product = new HashMap<>();

                int idProdotto = rs.getInt("id_prodotto");

                product.put("id_prodotto", idProdotto);

                product.put("nome", rs.getString("nome"));

                product.put("descrizione", rs.getString("descrizione"));

                product.put("prezzo_base", rs.getDouble("prezzo_base"));
 
               

                List<Map<String, Object>> features = new ArrayList<>();

                String sqlFeat = "SELECT id_caratteristica, nome, descrizione, " +

                                 "differenza_prezzo, is_default, id_gruppo " +

                                 "FROM Caratteristica WHERE id_prodotto = ?";

                PreparedStatement psFeat = conn.prepareStatement(sqlFeat);

                psFeat.setInt(1, idProdotto);

                ResultSet rsFeat = psFeat.executeQuery();
 
                while (rsFeat.next()) {

                    Map<String, Object> feat = new HashMap<>();

                    feat.put("id_caratteristica", rsFeat.getInt("id_caratteristica"));

                    feat.put("nome", rsFeat.getString("nome"));

                    feat.put("descrizione", rsFeat.getString("descrizione"));

                    feat.put("differenza_prezzo", rsFeat.getDouble("differenza_prezzo"));

                    feat.put("is_default", rsFeat.getBoolean("is_default"));

                    feat.put("id_gruppo", rsFeat.getObject("id_gruppo")); // può essere null

                    features.add(feat);

                }

                product.put("caratteristiche", features);

                products.add(product);

            }

            return Response.ok(products).build();
 
        } catch (SQLException e) {

            return Response.serverError()

                           .entity(Map.of("error", e.getMessage()))

                           .build();

        }

    }
 

    @GET

    @Path("/search")

    public Response searchProducts(

            @QueryParam("name") String name,

            @QueryParam("minPrice") Double minPrice,

            @QueryParam("maxPrice") Double maxPrice) {
 
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = DBConnect.getConnection()) {
 
            StringBuilder sql = new StringBuilder(

                "SELECT id_prodotto, nome, descrizione, prezzo_base FROM Prodotto WHERE 1=1");

            List<Object> params = new ArrayList<>();
 
            if (name != null && !name.isEmpty()) {

                sql.append(" AND nome LIKE ?");

                params.add("%" + name + "%");

            }

            if (minPrice != null) {

                sql.append(" AND prezzo_base >= ?");

                params.add(minPrice);

            }

            if (maxPrice != null) {

                sql.append(" AND prezzo_base <= ?");

                params.add(maxPrice);

            }
 
            PreparedStatement ps = conn.prepareStatement(sql.toString());

            for (int i = 0; i < params.size(); i++) {

                ps.setObject(i + 1, params.get(i));

            }
 
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                Map<String, Object> p = new HashMap<>();

                p.put("id_prodotto", rs.getInt("id_prodotto"));

                p.put("nome", rs.getString("nome"));

                p.put("descrizione", rs.getString("descrizione"));

                p.put("prezzo_base", rs.getDouble("prezzo_base"));

                results.add(p);

            }

            return Response.ok(results).build();
 
        } catch (SQLException e) {

            return Response.serverError()

                           .entity(Map.of("error", e.getMessage()))

                           .build();

        }

    }
 
    

    @GET

    @Path("/{prodId}/ingredients")

    public Response getIngredients(@PathParam("prodId") int prodId) {

        try (Connection conn = DBConnect.getConnection()) {
 
            String sql = "SELECT ingredienti FROM Prodotto WHERE id_prodotto = ?";

            PreparedStatement ps = conn.prepareStatement(sql);

            ps.setInt(1, prodId);

            ResultSet rs = ps.executeQuery();
 
            if (rs.next()) {

                return Response.ok(Map.of("ingredienti", rs.getString("ingredienti"))).build();

            } else {

                return Response.status(Response.Status.NOT_FOUND)

                               .entity(Map.of("error", "Prodotto non trovato"))

                               .build();

            }

        } catch (SQLException e) {

            return Response.serverError()

                           .entity(Map.of("error", e.getMessage()))

                           .build();

        }

    }
 
    

    @DELETE

    @Path("/{prodId}/features/{featId}")

    public Response deleteFeature(

            @PathParam("prodId") int prodId,

            @PathParam("featId") int featId) {
 
        try (Connection conn = DBConnect.getConnection()) {
 
            String sql = "DELETE FROM Caratteristica WHERE id_caratteristica = ? AND id_prodotto = ?";

            PreparedStatement ps = conn.prepareStatement(sql);

            ps.setInt(1, featId);

            ps.setInt(2, prodId);

            int rows = ps.executeUpdate();
 
            if (rows == 0) {

                return Response.status(Response.Status.NOT_FOUND)

                               .entity(Map.of("error", "Caratteristica non trovata"))

                               .build();

            }

            return Response.noContent().build();
 
        } catch (SQLException e) {

            return Response.serverError()

                           .entity(Map.of("error", e.getMessage()))

                           .build();

        }

    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response aggiungiProdottoMenu(Map<String, Object> payload, @HeaderParam("Authorization") String token) {
        
        // Verifica permessi: solo admin o staff possono aggiungere prodotti al menu
        Map<String, Object> user = AuthHelper.getUserFromToken(token);
        if (user == null || (!user.get("ruolo").equals("admin") && !user.get("ruolo").equals("staff"))) {
            return Response.status(Response.Status.FORBIDDEN)
                           .entity(Map.of("error", "Solo gli admin/staff possono aggiungere prodotti"))
                           .build();
        }

        try (Connection conn = DBConnect.getConnection()) {
            String nome = (String) payload.get("nome");
            String descrizione = (String) payload.get("descrizione");
            double prezzoBase = Double.parseDouble(payload.get("prezzo_base").toString());

            String sql = "INSERT INTO Prodotto (nome, descrizione, prezzo_base) VALUES (?, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, nome);
            ps.setString(2, descrizione);
            ps.setDouble(3, prezzoBase);
            ps.executeUpdate();
            
            ResultSet generatedKeys = ps.getGeneratedKeys();
            if (generatedKeys.next()) {
                long idGenerato = generatedKeys.getLong(1);
                return Response.status(Response.Status.CREATED)
                        .entity(Map.of("message", "Prodotto aggiunto al menu!", "id_prodotto", idGenerato))
                        .build();
            }
        } catch (SQLException e) {
            return Response.serverError()
                           .entity(Map.of("error", e.getMessage()))
                           .build();
        }
        return Response.status(Response.Status.BAD_REQUEST).build();
    }
}
 