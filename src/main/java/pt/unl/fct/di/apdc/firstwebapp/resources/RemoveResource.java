package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.RemoveData;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;
import jakarta.ws.rs.core.Response.Status;

@Path("/remove")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RemoveResource {

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("teak-advice-416614").build().getService();

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeRole(RemoveData data) {
        LOG.fine("Removal of user: " + data.targetID + " - attempted by user: " + data.userID);

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
                return Response.status(Status.CONFLICT).entity("User doesn't exists.").build();

            } else if (target == null) {
                txn.rollback();
                return Response.status(Status.CONFLICT).entity("Target user doesn't exists.").build();

            } else if (!data.authorizeChange(user.getString("user_role"), target.getString("user_role"))) {
                txn.rollback();
                return Response.status(Status.UNAUTHORIZED).entity("User doesn't have permission.").build();

            } else {

                txn.delete(targetKey);
                txn.commit();

                return Response.ok("User Removed with Success").build();
            }
        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            if(txn.isActive()) { txn.rollback(); }
        }
    }
}