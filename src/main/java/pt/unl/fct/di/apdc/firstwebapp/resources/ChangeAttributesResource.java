package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.Locale;
import java.util.logging.Logger;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Transaction;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Entity;

import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.AttributesChangeData;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


@Path("/change_attributes")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ChangeAttributesResource {

    private final Gson g = new Gson();

    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());

    DatastoreOptions options = DatastoreOptions.newBuilder().setProjectId("teak-advice-416614").build();
    Datastore datastore = options.getService();

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeAttributes (AttributesChangeData data) {
        LOG.fine("State change of user: " + data.targetID + " - attempted by user: " + data.userName);

        if (!data.validAttributesChange()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing or wrong parameter.").build();
        }

        Transaction txn = datastore.newTransaction();

        try {
            Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.userName);
            Entity user = txn.get(userKey);

            Key targetKey = datastore.newKeyFactory().setKind("User").newKey(data.targetID);
            Entity target = txn.get(targetKey);

            if (user == null) {
                txn.rollback();
                return Response.status(Response.Status.CONFLICT).entity("User doesn't exists.").build();

            } else if (target == null) {
                txn.rollback();
                return Response.status(Response.Status.CONFLICT).entity("Target user doesn't exists.").build();

            } else if (!data.authorizeAttributesChange(user.getString("user_Role"), target.getString("user_Role"))) {
                txn.rollback();
                return Response.status(Response.Status.UNAUTHORIZED).entity("User doesn't have permission.").build();

            } else {
                Entity.Builder newTarget = Entity.newBuilder(target);

                if (!user.getString("user_Role").equals("USER")) {
                    if (!data.email.isEmpty()) {
                        newTarget.set("user_Email", data.email);
                    }
                    if (!data.profile.isEmpty()) {
                        newTarget.set("user_profile", data.profile);
                    }
                    if (!data.role.isEmpty()) {
                        newTarget.set("user_Role", data.role.toUpperCase());
                    }
                    if (!data.state.isEmpty()) {
                        newTarget.set("user_State", data.state.equals("true"));
                    }
                }

                if (!data.phone.isEmpty()) {
                    newTarget.set("user_Phone", data.phone);
                }
                if (!data.password.isEmpty()) {
                    newTarget.set("user_Password", DigestUtils.sha512Hex(data.password));
                }
                if (!data.isPublic.isEmpty()) {
                    newTarget.set("is_Public", data.isPublic.equals("true"));
                }
                if (!data.occupation.isEmpty()) {
                    newTarget.set("occupation", data.occupation);
                }
                if (!data.workplace.isEmpty()) {
                    newTarget.set("workplace", data.workplace);
                }
                if (!data.address.isEmpty()) {
                    newTarget.set("address", data.address);
                }
                if (!data.postalCode.isEmpty()) {
                    newTarget.set("postal_Code", data.postalCode);
                }
                if (!data.NIF.isEmpty()) {
                    newTarget.set("NIF", data.NIF);
                }

                txn.put(newTarget.build());
                txn.commit();

                LOG.info("Successfully changed attributes of " + data.targetID);
                return Response.ok("Successfully updated user").build();
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