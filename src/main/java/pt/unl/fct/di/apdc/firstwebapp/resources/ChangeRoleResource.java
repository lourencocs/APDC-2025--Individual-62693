package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;

import pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData; // Use the refactored data class

@Path("/changerole")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ChangeRoleResource {

    private final Gson g = new Gson(); // Gson for potential future JSON response bodies
    private static final Logger LOG = Logger.getLogger(ChangeRoleResource.class.getName()); // Use correct logger name

    // Consider managing Datastore client more centrally (e.g., ServletContext, Dependency Injection)
    // Re-initializing per request (or per resource instance) can be inefficient.
    // Using the project ID from your previous code. Make sure this is correct.
    private final Datastore datastore = DatastoreOptions.newBuilder()
            .setProjectId("projetoadc-456513")
            .build()
            .getService();

    public ChangeRoleResource() {} // JAX-RS needs a public constructor

    @POST
    @Path("/") // Consider making the path more specific, e.g., "/v1" or "/update" if needed
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeRole(ChangeRoleData data) {

        // 1. Initial Input Validation
        if (data == null || !data.isValid()) {
            LOG.warning("Change role attempt with invalid data.");
            return Response.status(Status.BAD_REQUEST)
                    .entity("Invalid or missing input data. Ensure usernames are provided and newRole is valid (SU, GA, GBO, USER).")
                    .build();
        }

        LOG.fine("Attempting role change: " + data.usernameOne + " wants to change " + data.usernameTwo + " to " + data.newRole);

        Transaction txn = datastore.newTransaction();
        try {
            Key userOneKey = datastore.newKeyFactory().setKind("User").newKey(data.usernameOne);
            Key userTwoKey = datastore.newKeyFactory().setKind("User").newKey(data.usernameTwo);

            // Fetch both entities within the transaction
            Entity userOne = txn.get(userOneKey);
            Entity userTwo = txn.get(userTwoKey);

            // 2. Check if users exist
            if (userOne == null || userTwo == null) {
                txn.rollback(); // No need to proceed if users don't exist
                LOG.warning("Change role failed: One or both users not found (" + data.usernameOne + ", " + data.usernameTwo + ").");
                return Response.status(Status.NOT_FOUND) // Use NOT_FOUND if users are missing
                        .entity("Initiating user or target user not found.")
                        .build();
            }

            // Retrieve current roles
            String userOneRole = userOne.getString("user_role");
            String userTwoRole = userTwo.getString("user_role");

            // 3. Authorization Check (using the method in ChangeRoleData)
            if (!data.authorizeChange(userOneRole, userTwoRole)) {
                txn.rollback(); // Rollback before returning
                LOG.warning("Authorization failed: User " + data.usernameOne + " (Role: " + userOneRole +
                        ") cannot change user " + data.usernameTwo + " (Role: " + userTwoRole + ") to " + data.newRole);
                return Response.status(Status.FORBIDDEN) // Use FORBIDDEN for authorization issues
                        .entity("User " + data.usernameOne + " is not authorized to perform this role change.")
                        .build();
            }

            // 4. Perform the Update
            Entity updatedUserTwo = Entity.newBuilder(userTwoKey) // Build with key
                    .set("user_name", userTwo.getString("user_name")) // Keep other properties
                    .set("user_email", userTwo.getString("user_email"))
                    .set("user_phone", userTwo.getString("user_phone"))
                    .set("user_pwd", userTwo.getString("user_pwd"))
                    // ... copy other relevant properties ...
                    .set("user_state", userTwo.getBoolean("user_state"))
                    .set("user_role", data.newRole.toUpperCase()) // Set the new role (consistent case)
                    .build();
            txn.put(updatedUserTwo);
            txn.commit();

            LOG.info("Successfully changed role of user '" + data.usernameTwo + "' to '" + data.newRole.toUpperCase() + "' by user '" + data.usernameOne + "'.");
            return Response.ok("Successfully updated role for user: " + data.usernameTwo).build();

        } catch (DatastoreException e) {
            // Specific catch for Datastore issues
            if (txn.isActive()) {
                txn.rollback();
            }
            LOG.log(Level.SEVERE, "Datastore error during role change: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Datastore error during role change.").build();
        } catch (Exception e) {
            // General catch block
            if (txn.isActive()) {
                txn.rollback();
            }
            LOG.log(Level.SEVERE, "Unexpected error during role change: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("An unexpected error occurred.").build();
        } finally {
            // Ensure rollback happens if commit failed or another exception occurred mid-transaction
            // Removed the 'return' from here.
            if (txn.isActive()) {
                LOG.warning("Transaction was still active in finally block, rolling back.");
                txn.rollback();
            }
        }
    }
}