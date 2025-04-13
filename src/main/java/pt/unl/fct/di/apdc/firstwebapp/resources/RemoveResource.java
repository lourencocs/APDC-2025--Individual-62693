package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
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

    private static final String OPERATION_NAME = "OP5 - removeUser"; // Corrected operation name
    private static final String KIND_USER = "User";
    private static final String FIELD_EMAIL = "user_email";
    private static final String FIELD_ROLE = "user_role";

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response removeUser(RemoveData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {

        if (!data.isValid()) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing or wrong parameter (must provide userID or email to remove).");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }

        String tokenID = AuthUtil.extractTokenID(authHeader);
        if (tokenID == null) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing or invalid token.");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
        }

        Entity requestingUser = AuthUtil.validateToken(datastore, tokenID);
        if (requestingUser == null) {
            LOG.warning("Remove user request with invalid or expired token: " + tokenID);
            OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Invalid or expired token.");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
        }
        String loggedInUsername = requestingUser.getKey().getName();

        if (!loggedInUsername.equals(data.userID1)) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Unauthorized: Token does not match initiating user.");
            return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
        }

        Transaction txn = datastore.newTransaction();
        try {
            Key targetKey = null;
            Entity targetUser = null;

            if (data.userID2 != null && !data.userID2.trim().isEmpty()) {
                targetKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(data.userID2);
                targetUser = txn.get(targetKey);
            } else if (data.email != null && !data.email.trim().isEmpty()) {
                Query<Entity> query = Query.newEntityQueryBuilder()
                        .setKind(KIND_USER)
                        .setFilter(PropertyFilter.eq(FIELD_EMAIL, data.email))
                        .setLimit(1) // Assuming emails are unique
                        .build();
                QueryResults<Entity> results = txn.run(query);
                if (results.hasNext()) {
                    targetUser = results.next();
                    targetKey = targetUser.getKey();
                }
            }

            if (targetUser == null) {
                txn.rollback();
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "Target user not found with provided userID or email.");
                return Response.status(Status.CONFLICT).entity(g.toJson(errorResult)).build();
            }

            if (!data.authorizeChange(requestingUser.getString(FIELD_ROLE), targetUser.getString(FIELD_ROLE))) {
                txn.rollback();
                OpResult errorResult = new OpResult(OPERATION_NAME, data, tokenID, "User doesn't have permission to remove this account.");
                return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
            }

            txn.delete(targetKey);

            if (loggedInUsername.equals(targetUser.getKey().getName())) { // if user deletes itself.
                Key tokenToDeleteKey = datastore.newKeyFactory().setKind(AuthUtil.AUTH_TOKEN_KIND).newKey(tokenID);
                txn.delete(tokenToDeleteKey); // delete the users token.
                LOG.info("User " + loggedInUsername + " deleted itself and logged out.");
            }

            txn.commit();

            OpResult successResult = new OpResult(OPERATION_NAME, data, tokenID, "User Removed with Success");
            return Response.ok(g.toJson(successResult)).build();

        } catch (DatastoreException e) {
            if (txn.isActive()) txn.rollback();
            LOG.severe("Datastore error: " + e.getMessage());
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Datastore error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } catch (Exception e) {
            if (txn.isActive()) txn.rollback();
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