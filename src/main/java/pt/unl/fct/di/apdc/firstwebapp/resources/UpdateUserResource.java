package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil; // Use AuthUtil
import pt.unl.fct.di.apdc.firstwebapp.util.UpdateUserData; // Use UpdateUserData

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.logging.Logger;

@Path("/updateuser")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class UpdateUserResource {

    private static final Logger LOG = Logger.getLogger(UpdateUserResource.class.getName());
    private final Gson g = new Gson();
    // Consistent Project ID
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

    // Consistent Field Names & Roles (Consider defining these in a shared constants class)
    private static final String KIND_USER = "User";
    private static final String FIELD_EMAIL = "user_email";
    private static final String FIELD_USERID = "user_id";
    private static final String FIELD_USERNAME_DISPLAY = "user_name";
    private static final String FIELD_PROFILE = "user_profile";
    private static final String FIELD_PHONE = "user_phone";
    private static final String FIELD_PASSWORD = "user_pwd";
    private static final String FIELD_ROLE = "user_role";
    private static final String FIELD_STATE = "user_state";
    private static final String FIELD_IS_PUBLIC = "user_isPublic";
    private static final String FIELD_OCCUPATION = "occupation";
    private static final String FIELD_WORKPLACE = "workplace";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_POSTAL_CODE = "postal_Code";
    private static final String FIELD_NIF = "NIF";

    private static final String ROLE_USER = "USER";
    private static final String ROLE_GBO = "GBO";
    private static final String ROLE_GA = "GA";
    private static final String ROLE_SU = "SU";

    // Removed validateTokenAndGetUser - using AuthUtil

    @POST // Or PUT might be semantically better for update
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateUserAttributes(UpdateUserData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {

        String tokenID = AuthUtil.extractTokenID(authHeader);
        // Also remove authToken from UpdateUserData if it was added there
        if (data == null || data.targetUsername == null || data.targetUsername.isEmpty()) {
            return Response.status(Status.BAD_REQUEST).entity("Missing target username in request body.").build();
        }
        if (tokenID == null) {
            return Response.status(Status.BAD_REQUEST).entity("Missing Authorization Bearer token.").build();
        }
        LOG.fine("Attempting attribute update for target: " + data.targetUsername);


        // 1. Authentication
        Entity requestingUser = AuthUtil.validateToken(datastore, tokenID);
        if (requestingUser == null) {
            LOG.warning("Update request with invalid or expired token: " + tokenID);
            return Response.status(Status.UNAUTHORIZED).entity("Invalid or expired token.").build();
        }
        String requesterUsername = requestingUser.getKey().getName();
        String requesterRole = requestingUser.getString(FIELD_ROLE);
        LOG.info("Update request received from user: " + requesterUsername + " (Role: " + requesterRole + ") for target: " + data.targetUsername);

        // 2. Authorization & Target Validation within a Transaction
        Transaction txn = datastore.newTransaction();
        try {
            Key targetKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(data.targetUsername);
            Entity targetUser = txn.get(targetKey);

            // Check if target user exists
            if (targetUser == null) {
                txn.rollback();
                LOG.warning("Update failed: Target user '" + data.targetUsername + "' not found.");
                return Response.status(Status.NOT_FOUND).entity("Target user not found.").build();
            }
            String targetRole = targetUser.getString(FIELD_ROLE);
            boolean isSelfModification = requesterUsername.equals(data.targetUsername);

            // Check role-based permission (can requester target this target role?)
            if (!UpdateUserData.canTarget(requesterRole, targetRole, isSelfModification)) {
                txn.rollback();
                LOG.warning("Authorization failed: User " + requesterUsername + " (Role: " + requesterRole +
                        ") cannot modify user " + data.targetUsername + " (Role: " + targetRole + ").");
                return Response.status(Status.FORBIDDEN).entity("User does not have permission to modify the target user.").build();
            }

            // 3. Attribute Update Logic
            Entity.Builder builder = Entity.newBuilder(targetUser);
            boolean modified = false;

            // Apply changes based on rules - Using consistent field names
            if (data.profile != null) { builder.set(FIELD_PROFILE, data.profile); modified = true; }
            if (data.phone != null) { builder.set(FIELD_PHONE, data.phone); modified = true; }
            if (data.password != null && !data.password.isEmpty()) {
                // Password complexity validation could be added here
                builder.set(FIELD_PASSWORD, DigestUtils.sha512Hex(data.password)); modified = true;
            }
            if (data.isPublic != null) { builder.set(FIELD_IS_PUBLIC, data.isPublic); modified = true; }
            if (data.occupation != null) { builder.set(FIELD_OCCUPATION, data.occupation); modified = true; }
            if (data.workplace != null) { builder.set(FIELD_WORKPLACE, data.workplace); modified = true; }
            if (data.address != null) { builder.set(FIELD_ADDRESS, data.address); modified = true; }
            if (data.postalCode != null) { builder.set(FIELD_POSTAL_CODE, data.postalCode); modified = true; }
            if (data.NIF != null) { builder.set(FIELD_NIF, data.NIF); modified = true; }

            // Privileged Attributes: Role
            if (data.role != null) {
                if (isSelfModification) { // Users cannot change their own role
                    txn.rollback();
                    LOG.warning("Attribute update failed: User " + requesterUsername + " attempted to change own role.");
                    return Response.status(Status.FORBIDDEN).entity("Users cannot change their own role.").build();
                }
                String newRole = data.role.toUpperCase();
                // Validate new role value
                if (!(newRole.equals(ROLE_USER) || newRole.equals(ROLE_GBO) || newRole.equals(ROLE_GA) || newRole.equals(ROLE_SU))) { // SU can be set by SU
                    txn.rollback();
                    LOG.warning("Attribute update failed: Invalid role value provided: " + data.role);
                    return Response.status(Status.BAD_REQUEST).entity("Invalid role value provided.").build();
                }
                // Check permission to set the role
                boolean roleChangeAllowed = false;
                if (requesterRole.equals(ROLE_SU)) { // SU can set any valid role including SU
                    roleChangeAllowed = true;
                } else if (requesterRole.equals(ROLE_GA) && (newRole.equals(ROLE_GBO) || newRole.equals(ROLE_USER))) { // GA can set GBO or USER
                    roleChangeAllowed = true;
                } else if (requesterRole.equals(ROLE_GBO) && newRole.equals(ROLE_USER)) { // GBO can only set USER
                    roleChangeAllowed = true;
                }

                if(roleChangeAllowed) {
                    builder.set(FIELD_ROLE, newRole);
                    modified = true;
                } else {
                    txn.rollback();
                    LOG.warning("Authorization failed: Role " + requesterRole + " cannot set target role to " + newRole);
                    return Response.status(Status.FORBIDDEN).entity("Insufficient permission to set the requested role.").build();
                }
            }

            // Privileged Attributes: State
            if (data.state != null) {
                if (isSelfModification) { // Users cannot change their own state
                    txn.rollback();
                    LOG.warning("Attribute update failed: User " + requesterUsername + " attempted to change own state.");
                    return Response.status(Status.FORBIDDEN).entity("Users cannot change their own account state.").build();
                }
                // Only SU, GA, GBO can change state of others they can target
                builder.set(FIELD_STATE, data.state);
                modified = true;
            }

            // Immutable fields (Email, Name, Username Key) are not handled here

            // 4. Commit Transaction if modified
            if (modified) {
                txn.put(builder.build());
                txn.commit();
                LOG.info("Successfully updated attributes for user: " + data.targetUsername + " by user: " + requesterUsername);
                return Response.ok().entity("User attributes updated successfully.").build();
            } else {
                txn.rollback();
                LOG.info("No attributes were modified for user: " + data.targetUsername + " (request by " + requesterUsername + ")");
                return Response.ok().entity("No attributes provided or no changes needed.").build();
            }

        } catch (Exception e) {
            if (txn.isActive()) txn.rollback();
            LOG.severe("Error during attribute update for target " + data.targetUsername + ": " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Internal server error during attribute update.").build();
        } finally {
            if (txn.isActive()) txn.rollback();
        }
    }
}