package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;
import pt.unl.fct.di.apdc.firstwebapp.util.UpdateUserData;
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult; // Import OpResult

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
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

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
    private static final String OPERATION_NAME = "OP6 - updateUserAttributes"; // Operation Name.

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateUserAttributes(UpdateUserData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {

        String tokenID = AuthUtil.extractTokenID(authHeader);
        if (data == null || data.targetUserID == null || data.targetUserID.isEmpty()) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing target userID in request body.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }
        if (tokenID == null) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing Authorization Bearer token.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }
        LOG.fine("Attempting attribute update for target: " + data.targetUserID);

        Entity requestingUser = AuthUtil.validateToken(datastore, tokenID);
        if (requestingUser == null) {
            LOG.warning("Update request with invalid or expired token: " + tokenID);
            OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Invalid or expired token.");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
        }
        String requesterUsername = requestingUser.getKey().getName();
        String requesterRole = requestingUser.getString(FIELD_ROLE);
        LOG.info("Update request received from user: " + requesterUsername + " (Role: " + requesterRole + ") for target: " + data.targetUserID);

        Transaction txn = datastore.newTransaction();
        try {
            Key targetKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(data.targetUserID);
            Entity targetUser = txn.get(targetKey);

            if (targetUser == null) {
                txn.rollback();
                LOG.warning("Update failed: Target user '" + data.targetUserID + "' not found.");
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Target user not found.");
                return Response.status(Status.NOT_FOUND).entity(g.toJson(errorResult)).build();
            }
            String targetRole = targetUser.getString(FIELD_ROLE);
            boolean isSelfModification = requesterUsername.equals(data.targetUserID);

            if (!UpdateUserData.canTarget(requesterRole, targetRole, isSelfModification)) {
                txn.rollback();
                LOG.warning("Authorization failed: User " + requesterUsername + " (Role: " + requesterRole + ") cannot modify user " + data.targetUserID + " (Role: " + targetRole + ").");
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "User does not have permission to modify the target user.");
                return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
            }

            Entity.Builder builder = Entity.newBuilder(targetUser);
            boolean modified = false;

            if (data.profile != null) { builder.set(FIELD_PROFILE, data.profile); modified = true; }
            if (data.phone != null) { builder.set(FIELD_PHONE, data.phone); modified = true; }
            if (data.password != null && !data.password.isEmpty()) {
                builder.set(FIELD_PASSWORD, DigestUtils.sha512Hex(data.password)); modified = true;
            }
            if (data.isPublic != null) { builder.set(FIELD_IS_PUBLIC, data.isPublic); modified = true; }
            if (data.occupation != null) { builder.set(FIELD_OCCUPATION, data.occupation); modified = true; }
            if (data.workplace != null) { builder.set(FIELD_WORKPLACE, data.workplace); modified = true; }
            if (data.address != null) { builder.set(FIELD_ADDRESS, data.address); modified = true; }
            if (data.postalCode != null) { builder.set(FIELD_POSTAL_CODE, data.postalCode); modified = true; }
            if (data.NIF != null) { builder.set(FIELD_NIF, data.NIF); modified = true; }

            if (data.role != null) {
                if (isSelfModification) {
                    txn.rollback();
                    LOG.warning("Attribute update failed: User " + requesterUsername + " attempted to change own role.");
                    OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Users cannot change their own role.");
                    return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
                }
                String newRole = data.role.toUpperCase();
                if (!(newRole.equals(ROLE_USER) || newRole.equals(ROLE_GBO) || newRole.equals(ROLE_GA) || newRole.equals(ROLE_SU))) {
                    txn.rollback();
                    LOG.warning("Attribute update failed: Invalid role value provided: " + data.role);
                    OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Invalid role value provided.");
                    return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
                }
                boolean roleChangeAllowed = false;
                if (requesterRole.equals(ROLE_SU)) {
                    roleChangeAllowed = true;
                } else if (requesterRole.equals(ROLE_GA) && (newRole.equals(ROLE_GBO) || newRole.equals(ROLE_USER))) {
                    roleChangeAllowed = true;
                } else if (requesterRole.equals(ROLE_GBO) && newRole.equals(ROLE_USER)) {
                    roleChangeAllowed = true;
                }

                if(roleChangeAllowed) {
                    builder.set(FIELD_ROLE, newRole);
                    modified = true;
                } else {
                    txn.rollback();
                    LOG.warning("Authorization failed: Role " + requesterRole + " cannot set target role to " + newRole);
                    OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Insufficient permission to set the requested role.");
                    return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
                }
            }

            if (data.state != null) {
                if (isSelfModification) {
                    txn.rollback();
                    LOG.warning("Attribute update failed: User " + requesterUsername + " attempted to change own state.");
                    OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Users cannot change their own account state.");
                    return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
                }
                builder.set(FIELD_STATE, data.state);
                modified = true;
            }

            if (modified) {
                txn.put(builder.build());
                txn.commit();
                LOG.info("Successfully updated attributes for user: " + data.targetUserID + " by user: " + requesterUsername);
                OpResult successResult = new OpResult(OPERATION_NAME, data, null, "User attributes updated successfully.");
                return Response.ok(g.toJson(successResult)).build();
            } else {
                txn.rollback();
                LOG.info("No attributes were modified for user: " + data.targetUserID + " (request by " + requesterUsername + ")");
                OpResult successResult = new OpResult(OPERATION_NAME, data, null, "No attributes provided or no changes needed.");
                return Response.ok(g.toJson(successResult)).build();
            }

        } catch (Exception e) {
            if (txn.isActive()) txn.rollback();
            LOG.severe("Error during attribute update for target " + data.targetUserID + ": " + e.getMessage());
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Internal server error during attribute update.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } finally {
            if (txn.isActive()) txn.rollback();
        }
    }
}