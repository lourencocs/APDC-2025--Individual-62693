package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;

import pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData; // Uses updated class
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;

@Path("/changerole")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ChangeRoleResource {

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(ChangeRoleResource.class.getName());
    private final Datastore datastore = DatastoreOptions.newBuilder()
            .setProjectId("projetoadc-456513")
            .build()
            .getService();

    private static final String OPERATION_NAME = "OP3 - changeRole";

    private static final String KIND_USER = "User";
    private static final String FIELD_ROLE = "user_role";
    private static final String FIELD_STATE = "user_state";

    public ChangeRoleResource() {}

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeRole(ChangeRoleData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {

        if (data == null || !data.isValid()) {
            LOG.warning("Change role attempt with invalid data or invalid target role.");
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Invalid or missing input data, or invalid target role.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }

        LOG.fine("Attempting role change: User '" + data.userID1 + "' wants to change user '" + data.userID2 + "' to role '" + data.newRole + "'.");

        String tokenID = AuthUtil.extractTokenID(authHeader);
        if (tokenID == null) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing or invalid token.");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
        }
        Key tokenKey = datastore.newKeyFactory().setKind(AuthUtil.AUTH_TOKEN_KIND).newKey(tokenID);
        Entity tokenEntity = datastore.get(tokenKey); // Use datastore.get for single key lookup (no transaction needed yet)

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
            Key userOneKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(data.userID1);
            Key userTwoKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(data.userID2);

            Entity userOne = txn.get(userOneKey);
            Entity userTwo = txn.get(userTwoKey);

            if (userOne == null || userTwo == null) {
                txn.rollback();
                LOG.warning("Change role failed: Initiating user '" + data.userID1 + "' or target user '" + data.userID2 + "' not found.");
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Initiating user or target user not found.");
                return Response.status(Status.NOT_FOUND).entity(g.toJson(errorResult)).build();
            }

            String userOneRole = userOne.getString(FIELD_ROLE);
            String userTwoRole = userTwo.getString(FIELD_ROLE);

            if (!data.authorizeChange(userOneRole, userTwoRole)) {
                txn.rollback();
                LOG.warning("Authorization failed: User '" + data.userID1 + "' (Role: " + userOneRole + ") is not authorized to change user '" + data.userID2 + "' (Role: " + userTwoRole + ") to role '" + data.newRole + "'.");
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "User is not authorized to perform this role change.");
                return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
            }

            Entity updatedUserTwo = Entity.newBuilder(userTwoKey, userTwo)
                    .set(FIELD_ROLE, data.newRole.toUpperCase())
                    .build();

            txn.put(updatedUserTwo);
            txn.commit();

            LOG.info("Successfully changed role of user '" + data.userID2 + "' to '" + data.newRole.toUpperCase() + "' by user '" + data.userID1 + "'.");
            OpResult successResult = new OpResult(OPERATION_NAME, data, tokenID, "Successfully updated role for user: " + data.userID2);
            return Response.ok(g.toJson(successResult)).build();

        } catch (DatastoreException e) {
            if (txn.isActive()) txn.rollback();
            LOG.log(Level.SEVERE, "Datastore error during role change for target user " + data.userID2 + ": " + e.getMessage(), e);
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Datastore error during role change.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } catch (Exception e) {
            if (txn.isActive()) txn.rollback();
            LOG.log(Level.SEVERE, "Unexpected error during role change for target user " + data.userID2 + ": " + e.getMessage(), e);
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "An unexpected error occurred.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } finally {
            if (txn.isActive()) {
                LOG.warning("Transaction was still active in finally block for role change initiated by " + data.userID1 + ", rolling back.");
                txn.rollback();
            }
        }
    }
}