package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pt.unl.fct.di.apdc.firstwebapp.util.RemoveData;
import java.util.logging.Logger;

@Path("/remove")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RemoveResource {

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(RemoveResource.class.getName());
    private final Datastore datastore = DatastoreOptions.newBuilder()
            .setProjectId("projetoadc-456513") // Ensure this project ID is correct
            .build()
            .getService(); // Use default instance for local testing

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeRole(RemoveData data) {
        LOG.info("Removal of user: " + data.targetID + " - attempted by user: " + data.userID);

        if (!data.validChange()) {
            return Response.status(Status.BAD_REQUEST).entity("Missing or wrong parameter.").build();
        }

        Transaction txn = datastore.newTransaction();

        try {
            Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.userID);
            Entity user = txn.get(userKey);
            Key targetKey = datastore.newKeyFactory().setKind("User").newKey(data.targetID);
            Entity target = txn.get(targetKey);

            if (user == null) {
                txn.rollback();
                return Response.status(Status.CONFLICT).entity("User doesn't exist.").build();
            }

            if (target == null) {
                txn.rollback();
                return Response.status(Status.CONFLICT).entity("Target user doesn't exist.").build();
            }

            if (!data.authorizeChange(user.getString("user_role"), target.getString("user_role"))) {
                txn.rollback();
                return Response.status(Status.UNAUTHORIZED).entity("User doesn't have permission.").build();
            }

            txn.delete(targetKey);
            txn.commit();

            return Response.ok("User Removed with Success").build();

        } catch (DatastoreException e) {
            txn.rollback();
            LOG.severe("Datastore error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Datastore error: " + e.getMessage()).build();
        } catch (Exception e) {
            txn.rollback();
            LOG.severe("Server error: " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Server error: " + e.getMessage()).build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}