package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil; // Use AuthUtil
import pt.unl.fct.di.apdc.firstwebapp.util.PasswordChangeData; // Use updated data class

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.logging.Logger;

// Path more specific
@Path("/password")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ChangePasswordResource {

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(ChangePasswordResource.class.getName());
    // Consistent Project ID
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

    // Consistent Field Names
    private static final String KIND_USER = "User";
    private static final String FIELD_PASSWORD = "user_pwd";


    @POST
    @Path("/change") // More specific path
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changePassword(PasswordChangeData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {

        String tokenID = AuthUtil.extractTokenID(authHeader);
        if (tokenID == null && (data == null || data.authToken == null)) {
            return Response.status(Status.BAD_REQUEST).entity("Missing Authorization token (Header or Body).").build();
        }
        // Prefer Header token if present
        String effectiveTokenID = (tokenID != null) ? tokenID : data.authToken;

        LOG.fine("Password change attempt via token: " + effectiveTokenID);

        // Validate password data object itself (checks new password match)
        if (data == null || !data.validPasswordChange()) {
            return Response.status(Status.BAD_REQUEST).entity("Missing parameters, or new passwords do not match.").build();
        }

        // Add complexity check for new password here if desired
        // if (!isValidPasswordComplexity(data.newPassword)) { return Response.status(Status.BAD_REQUEST)... }


        // 1. Authentication - Validate token to identify the user
        Entity user = AuthUtil.validateToken(datastore, effectiveTokenID);
        if (user == null) {
            LOG.warning("Password change request with invalid or expired token: " + effectiveTokenID);
            return Response.status(Status.UNAUTHORIZED).entity("Invalid or expired token.").build();
        }
        String username = user.getKey().getName();
        LOG.info("Password change initiated for user: " + username);


        // 2. Verify Current Password & Update within Transaction
        Transaction txn = datastore.newTransaction();
        try {
            // Re-fetch user within transaction for consistency
            Key userKey = user.getKey(); // Get key from entity fetched during validation
            Entity transactionalUser = txn.get(userKey);

            // Should not happen if token validation passed, but double-check
            if (transactionalUser == null) {
                txn.rollback(); // Should not happen
                LOG.severe("User " + username + " disappeared during password change transaction.");
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity("User consistency error.").build();
            }

            // Verify current password
            String storedPasswordHash = transactionalUser.getString(FIELD_PASSWORD);
            if (!storedPasswordHash.equals(DigestUtils.sha512Hex(data.currentPassword))) {
                txn.rollback(); // Important: Rollback before returning
                LOG.warning("Password change failed for user " + username + ": Incorrect current password.");
                return Response.status(Status.FORBIDDEN).entity("Incorrect current password.").build();
            }

            // Check if new password is the same as the old one
            if (storedPasswordHash.equals(DigestUtils.sha512Hex(data.newPassword))) {
                txn.rollback();
                LOG.info("Password change attempt for user " + username + ": New password is the same as the old one.");
                return Response.status(Status.BAD_REQUEST).entity("New password cannot be the same as the current password.").build();
            }


            // Update password
            Entity updatedUser = Entity.newBuilder(transactionalUser)
                    .set(FIELD_PASSWORD, DigestUtils.sha512Hex(data.newPassword))
                    .build();

            txn.put(updatedUser);
            txn.commit();

            LOG.info("Password changed successfully for user: " + username);
            // Optional: Revoke all other existing tokens for this user upon password change for security
            // revokeOtherTokens(datastore, username, effectiveTokenID);

            return Response.ok("Password changed successfully.").build();

        } catch (Exception e) {
            if (txn.isActive()) txn.rollback();
            LOG.severe("Error during password change for user " + username + ": " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Internal server error during password change.").build();
        } finally {
            if (txn.isActive()) txn.rollback();
        }
    }

    // private boolean isValidPasswordComplexity(String password) { ... }
    // private void revokeOtherTokens(Datastore ds, String username, String currentTokenToKeep) { ... }
}