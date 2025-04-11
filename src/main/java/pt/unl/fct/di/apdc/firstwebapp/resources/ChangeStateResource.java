package pt.unl.fct.di.apdc.firstwebapp.resources;

import java.util.logging.Logger;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.firstwebapp.resources.LoginResource;
import pt.unl.fct.di.apdc.firstwebapp.util.ChangeStateData;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


@Path("/changestate")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ChangeStateResource {

    private final Gson g = new Gson();

    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());

    DatastoreOptions options = DatastoreOptions.newBuilder().setProjectId("teak-advice-416614").build();
    Datastore datastore = options.getService();


    public ChangeStateResource() {}

    //change role -> post, altera a DB
    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changeState (ChangeStateData data) {

        //user x quer trocar role de user Y para Z
        Transaction txn = datastore.newTransaction();

        Key userOneKey = datastore.newKeyFactory().setKind("User").newKey(data.usernameOne);
        Entity userOne = txn.get(userOneKey);

        String userOneRole = (String) userOne.getString("user_role");

        Key userTwoKey = datastore.newKeyFactory().setKind("User").newKey(data.usernameTwo);
        Entity userTwo = txn.get(userTwoKey);

        String userTwoRole= (String) userTwo.getString("user_role");

        boolean userTwoState = userTwo.getBoolean("user_state");


        try {

            if(userOne == null){
                //username does not exist
                txn.rollback();
                return Response.status(Response.Status.FORBIDDEN).entity("User not found.").build();
            }

            if(userTwo == null){
                //username does not exist
                txn.rollback();
                return Response.status(Response.Status.FORBIDDEN).entity("User not found.").build();
            }

            if(!data.authorizeStateChange(userOneRole, userTwoRole)) {
                txn.rollback();

                return Response.status(Response.Status.CONFLICT).
                        entity("User not authorized.").build();
            }

            else { //change user two's role

                Entity newUserTwo = Entity.newBuilder(userTwo)
                        .set("user_state", !userTwoState)
                        .build();
                txn.put(newUserTwo);
                txn.commit();

                LOG.info("Successfully changed state of " + data.usernameTwo);

                return Response.ok("Successfully updated user").build();

            }

        } catch (Exception e) {
            txn.rollback();
            LOG.severe(e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            if(txn.isActive()) {
                txn.rollback();
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        }
    }

}