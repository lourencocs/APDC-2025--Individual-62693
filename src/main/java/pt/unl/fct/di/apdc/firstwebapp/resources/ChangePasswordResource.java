package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Transaction;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Entity;

import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.PasswordChangeData;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;
import jakarta.ws.rs.core.Response.Status;

@Path("/change_password")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ChangePasswordResource {

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("double-insight-417113").build().getService();

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeRole(PasswordChangeData data) {
        LOG.fine("Password change attempt by user: " + data.userID);

        if (!data.validPasswordChange()) {
            return Response.status(Status.BAD_REQUEST).entity("Missing or wrong parameter.").build();
        }

        Transaction txn = datastore.newTransaction();

        try {
            Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.userID);
            Entity user = txn.get(userKey);
            if (user == null) {
                txn.rollback();
                return Response.status(Status.CONFLICT).entity("User doesn't exists.").build();
            } else if (!user.getString("user_Password").equals(DigestUtils.sha512Hex(data.password1))) {
                txn.rollback();
                return Response.status(Status.FORBIDDEN).entity("Wrong password for username: " + data.userID).build();
            } else {
                Entity newUser = Entity.newBuilder(user)
                        .set("user_Password", DigestUtils.sha512Hex(data.newPassword))
                        .build();

                txn.put(newUser);
                txn.commit();
                return Response.ok("Password Changed with Success").build();
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