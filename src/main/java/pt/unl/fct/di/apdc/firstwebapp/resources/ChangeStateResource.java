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

// Import the refactored data class
import pt.unl.fct.di.apdc.firstwebapp.util.ChangeStateData;
// Assuming role constants might be needed if checking roles directly here later
// import static pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData.*;

@Path("/changestate")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ChangeStateResource {

    private final Gson g = new Gson(); // For potential JSON responses
    private static final Logger LOG = Logger.getLogger(ChangeStateResource.class.getName()); // Correct logger name

    // Consider managing Datastore client more centrally (e.g., ServletContext, Dependency Injection)
    private final Datastore datastore = DatastoreOptions.newBuilder()
            .setProjectId("projetoadc-456513") // Ensure this project ID is correct
            .build()
            .getService();

    public ChangeStateResource() {} // Public constructor for JAX-RS

    @POST
    @Path("/") // Consider making path more specific if needed e.g. "/toggle"
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeState(ChangeStateData data) {

        // 1. Initial Input Validation
        if (data == null || !data.isValid()) {
            LOG.warning("Change state attempt with invalid data (missing usernames).");
            return Response.status(Status.BAD_REQUEST)
                    .entity("Invalid or missing input data. Usernames must be provided.")
                    .build();
        }

        LOG.fine("Attempting state change: User '" + data.usernameOne + "' wants to toggle state for user '" + data.usernameTwo + "'.");

        Transaction txn = datastore.newTransaction();
        try {
            Key userOneKey = datastore.newKeyFactory().setKind("User").newKey(data.usernameOne);
            Key userTwoKey = datastore.newKeyFactory().setKind("User").newKey(data.usernameTwo);

            // Fetch both entities within the transaction
            Entity userOne = txn.get(userOneKey);
            Entity userTwo = txn.get(userTwoKey);

            // 2. Check if users exist
            if (userOne == null) {
                txn.rollback(); // No need to proceed
                LOG.warning("Change state failed: Initiating user not found (" + data.usernameOne + ").");
                // Usually return NOT_FOUND, but if the *initiator* must exist to even try, FORBIDDEN could be argued. Let's stick to NOT_FOUND for consistency.
                return Response.status(Status.NOT_FOUND)
                        .entity("Initiating user (" + data.usernameOne + ") not found.")
                        .build();
            }
            if (userTwo == null) {
                txn.rollback(); // No need to proceed
                LOG.warning("Change state failed: Target user not found (" + data.usernameTwo + ").");
                return Response.status(Status.NOT_FOUND)
                        .entity("Target user (" + data.usernameTwo + ") not found.")
                        .build();
            }

            // Retrieve current roles and target's state
            String userOneRole = userOne.getString("user_role");
            String userTwoRole = userTwo.getString("user_role");
            boolean userTwoCurrentState = userTwo.getBoolean("user_state"); // Get current state

            // 3. Authorization Check
            if (!data.authorizeStateChange(userOneRole, userTwoRole)) {
                txn.rollback(); // Rollback before returning
                LOG.warning("Authorization failed: User " + data.usernameOne + " (Role: " + userOneRole +
                        ") cannot change state for user " + data.usernameTwo + " (Role: " + userTwoRole + ")");
                return Response.status(Status.FORBIDDEN) // Use FORBIDDEN for authorization issues
                        .entity("User " + data.usernameOne + " is not authorized to change the state of user " + data.usernameTwo + ".")
                        .build();
            }

            // 4. Perform the Update - **CRITICAL FIX HERE**
            boolean newUserTwoState = !userTwoCurrentState; // Calculate the new state (toggle)

            // Rebuild the entity, copying all properties and setting the new state
            Entity.Builder builder = Entity.newBuilder(userTwoKey); // Use the Key!

            // Copy all existing properties from userTwo to the builder
            userTwo.getProperties().forEach(builder::set);

            // Set the new state
            builder.set("user_state", newUserTwoState);

            // Build the final entity
            Entity updatedUserTwo = builder.build();

            txn.put(updatedUserTwo); // Put the complete updated entity
            txn.commit();

            LOG.info("Successfully changed state of user '" + data.usernameTwo + "' to " +
                    (newUserTwoState ? "ACTIVE" : "INACTIVE") + " by user '" + data.usernameOne + "'.");
            return Response.ok("Successfully updated state for user: " + data.usernameTwo + " to " + (newUserTwoState ? "ACTIVE" : "INACTIVE")).build();

        } catch (DatastoreException e) {
            if (txn.isActive()) txn.rollback();
            LOG.log(Level.SEVERE, "Datastore error during state change: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Datastore error during state change.").build();
        } catch (Exception e) {
            if (txn.isActive()) txn.rollback();
            LOG.log(Level.SEVERE, "Unexpected error during state change: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("An unexpected error occurred.").build();
        } finally {
            // Just ensure rollback if transaction is still active
            if (txn.isActive()) {
                LOG.warning("Transaction was still active in finally block, rolling back.");
                txn.rollback();
            }
        }
    }
}