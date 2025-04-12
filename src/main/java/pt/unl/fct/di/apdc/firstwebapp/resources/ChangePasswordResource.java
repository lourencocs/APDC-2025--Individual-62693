package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;
import pt.unl.fct.di.apdc.firstwebapp.util.PasswordChangeData;
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult; // Import OpResult

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.logging.Logger;

@Path("/password")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ChangePasswordResource {

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(ChangePasswordResource.class.getName());
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

    private static final String KIND_USER = "User";
    private static final String FIELD_PASSWORD = "user_pwd";
    private static final String OPERATION_NAME = "OP7 - changePassword"; // Operation name

    @POST
    @Path("/change")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changePassword(PasswordChangeData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {

        String tokenID = AuthUtil.extractTokenID(authHeader);
        if (tokenID == null && (data == null || data.authToken == null)) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing Authorization token (Header or Body).");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }
        String effectiveTokenID = (tokenID != null) ? tokenID : data.authToken;

        LOG.fine("Password change attempt via token: " + effectiveTokenID);

        if (data == null || !data.validPasswordChange()) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing parameters, or new passwords do not match.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }

        Entity user = AuthUtil.validateToken(datastore, effectiveTokenID);
        if (user == null) {
            LOG.warning("Password change request with invalid or expired token: " + effectiveTokenID);
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Invalid or expired token.");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
        }
        String username = user.getKey().getName();
        LOG.info("Password change initiated for user: " + username);

        Transaction txn = datastore.newTransaction();
        try {
            Key userKey = user.getKey();
            Entity transactionalUser = txn.get(userKey);

            if (transactionalUser == null) {
                txn.rollback();
                LOG.severe("User " + username + " disappeared during password change transaction.");
                OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "User consistency error.");
                return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
            }

            String storedPasswordHash = transactionalUser.getString(FIELD_PASSWORD);
            if (!storedPasswordHash.equals(DigestUtils.sha512Hex(data.currentPassword))) {
                txn.rollback();
                LOG.warning("Password change failed for user " + username + ": Incorrect current password.");
                OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Incorrect current password.");
                return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
            }

            if (storedPasswordHash.equals(DigestUtils.sha512Hex(data.newPassword))) {
                txn.rollback();
                LOG.info("Password change attempt for user " + username + ": New password is the same as the old one.");
                OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "New password cannot be the same as the current password.");
                return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
            }

            Entity updatedUser = Entity.newBuilder(transactionalUser)
                    .set(FIELD_PASSWORD, DigestUtils.sha512Hex(data.newPassword))
                    .build();

            txn.put(updatedUser);
            txn.commit();

            LOG.info("Password changed successfully for user: " + username);
            OpResult successResult = new OpResult(OPERATION_NAME, data, null, "Password changed successfully.");
            return Response.ok(g.toJson(successResult)).build();

        } catch (Exception e) {
            if (txn.isActive()) txn.rollback();
            LOG.severe("Error during password change for user " + username + ": " + e.getMessage());
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Internal server error during password change.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } finally {
            if (txn.isActive()) txn.rollback();
        }
    }
}