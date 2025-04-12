package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pt.unl.fct.di.apdc.firstwebapp.util.RemoveData;
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;
import java.util.logging.Logger;

@Path("/remove")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RemoveResource {

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(RemoveResource.class.getName());
    private final Datastore datastore = DatastoreOptions.newBuilder()
            .setProjectId("projetoadc-456513")
            .build()
            .getService();

    private static final String OPERATION_NAME = "OP4 - removeUser";

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response removeUser(RemoveData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {

        if (!data.isValid()) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing or wrong parameter.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }

        String tokenID = AuthUtil.extractTokenID(authHeader);
        if (tokenID == null) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing or invalid token.");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
        }

        Key tokenKey = datastore.newKeyFactory().setKind(AuthUtil.AUTH_TOKEN_KIND).newKey(tokenID);
        Entity tokenEntity = datastore.get(tokenKey);

        if (tokenEntity == null) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Token not found.");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
        }

        long expirationDate = tokenEntity.getLong("expiration_date");
        if (expirationDate < System.currentTimeMillis()) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Token expired.");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
        }

        String loggedInUsername = tokenEntity.getString("user_username");

        if (!loggedInUsername.equals(data.userID1)) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Unauthorized: Token does not match initiating user.");
            return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
        }

        Transaction txn = datastore.newTransaction();
        try {
            Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.userID1);
            Entity user = txn.get(userKey);
            Key targetKey = datastore.newKeyFactory().setKind("User").newKey(data.userID2);
            Entity target = txn.get(targetKey);

            if (user == null) {
                txn.rollback();
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Initiating user doesn't exist.");
                return Response.status(Status.CONFLICT).entity(g.toJson(errorResult)).build();
            }

            if (target == null) {
                txn.rollback();
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Target user doesn't exist.");
                return Response.status(Status.CONFLICT).entity(g.toJson(errorResult)).build();
            }

            if (!data.authorizeChange(user.getString("user_role"), target.getString("user_role"))) {
                txn.rollback();
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "User doesn't have permission.");
                return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
            }

            txn.delete(targetKey);

            if (loggedInUsername.equals(data.userID2)) { // if user deletes itself.
                txn.delete(tokenKey); // delete the users token.
                LOG.info("User " + loggedInUsername + " deleted itself and logout.");
            }

            txn.commit();

            OpResult successResult = new OpResult(OPERATION_NAME, data, tokenID, "User Removed with Success");
            return Response.ok(g.toJson(successResult)).build();

        } catch (DatastoreException e) {
            txn.rollback();
            LOG.severe("Datastore error: " + e.getMessage());
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Datastore error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } catch (Exception e) {
            txn.rollback();
            LOG.severe("Server error: " + e.getMessage());
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Server error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}