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

import pt.unl.fct.di.apdc.firstwebapp.util.ChangeStateData; // Uses updated class
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;

@Path("/changestate")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ChangeStateResource {

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(ChangeStateResource.class.getName());
    private final Datastore datastore = DatastoreOptions.newBuilder()
            .setProjectId("projetoadc-456513")
            .build()
            .getService();

    private static final String OPERATION_NAME = "OP4 - changeState";

    private static final String KIND_USER = "User";
    private static final String FIELD_ROLE = "user_role";
    private static final String FIELD_STATE = "user_state";

    private static final String STATE_ACTIVE = "active";
    private static final String STATE_INACTIVE = "inactive";
    private static final String STATE_SUSPENDED = "suspended";


    public ChangeStateResource() {}

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeState(ChangeStateData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {

        if (data == null || !data.isValid()) {
            LOG.warning("Change state attempt with invalid data (missing userIDs).");
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Invalid or missing input data. UserIDs must be provided.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }

        LOG.fine("Attempting state change: User '" + data.userID1 + "' wants to toggle state for user '" + data.userID2 + "'.");

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
            Key userOneKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(data.userID1);
            Key userTwoKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(data.userID2);

            Entity userOne = txn.get(userOneKey);
            Entity userTwo = txn.get(userTwoKey);

            if (userOne == null) {
                txn.rollback();
                LOG.warning("Change state failed: Initiating user not found (" + data.userID1 + ").");
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Initiating user (" + data.userID1 + ") not found.");
                return Response.status(Status.NOT_FOUND).entity(g.toJson(errorResult)).build();
            }
            if (userTwo == null) {
                txn.rollback();
                LOG.warning("Change state failed: Target user not found (" + data.userID2 + ").");
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Target user (" + data.userID2 + ") not found.");
                return Response.status(Status.NOT_FOUND).entity(g.toJson(errorResult)).build();
            }

            String userOneRole = userOne.getString(FIELD_ROLE);
            String userTwoCurrentState = userTwo.contains(FIELD_STATE) ? userTwo.getString(FIELD_STATE) : STATE_INACTIVE;

            if (!data.authorizeStateChange(userOneRole, userTwoCurrentState)) {
                txn.rollback();
                LOG.warning("Authorization failed: User " + data.userID1 + " (Role: " + userOneRole + ") is not authorized to change account state to '" + data.newState + "' from '" + userTwoCurrentState + "'.");
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "User " + data.userID1 + " is not authorized to perform this action on user " + data.userID2 + ".");
                return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
            }

            // Validate the requested new state
            String requestedNewState = data.newState.trim().toLowerCase();
            if (!requestedNewState.equals(STATE_ACTIVE) &&
                    !requestedNewState.equals(STATE_INACTIVE) &&
                    !requestedNewState.equals(STATE_SUSPENDED)) {
                txn.rollback();
                LOG.warning("Invalid new state requested: " + data.newState);
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Invalid requested state: " + data.newState + ". Allowed states are: active, inactive, suspended.");
                return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
            }

            Entity updatedUserTwo = Entity.newBuilder(userTwoKey, userTwo)
                    .set(FIELD_STATE, requestedNewState)
                    .build();

            txn.put(updatedUserTwo);
            txn.commit();

            LOG.info("Successfully changed state of user '" + data.userID2 + "' to '" + requestedNewState + "' by user '" + data.userID1 + "'.");
            OpResult successResult = new OpResult(OPERATION_NAME, data, tokenID, "Successfully updated state for user: " + data.userID2 + " to " + requestedNewState);
            return Response.ok(g.toJson(successResult)).build();


        } catch (DatastoreException e) {
            if (txn.isActive()) txn.rollback();
            LOG.log(Level.SEVERE, "Datastore error during state change for target user " + data.userID2 + ": " + e.getMessage(), e);
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Datastore error during state change.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } catch (Exception e) {
            if (txn.isActive()) txn.rollback();
            LOG.log(Level.SEVERE, "Unexpected error during state change for target user " + data.userID2 + ": " + e.getMessage(), e);
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "An unexpected error occurred.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } finally {
            if (txn.isActive()) {
                LOG.warning("Transaction was still active in finally block for state change initiated by " + data.userID1 + ", rolling back.");
                txn.rollback();
            }
        }
    }
}