package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.RegisterData;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;
import jakarta.ws.rs.core.Response.Status;

@Path("/register")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RegisterResource {

    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());

    private final Gson g = new Gson();
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("teak-advice-416614").build().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory();


    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegister(RegisterData data){
        LOG.fine("Register attempt by user: " + data.username);

        if(!data.validRegistration()) {
            return Response.status(Status.BAD_REQUEST).entity("Missing or wrong parameter.").build();
        }

        Transaction txn = datastore.newTransaction();

        try{
            Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username);
            Entity user = txn.get(userKey);
            if(user != null) {
                txn.rollback();
                return Response.status(Status.CONFLICT).entity("User already exists.").build();
            }
            else{
                user = Entity.newBuilder(userKey)
                        .set("user_name", data.username)
                        .set("user_email", data.email)
                        .set("user_profile", data.profile)
                        .set("user_phone", data.phone)
                        .set("user_pwd", DigestUtils.sha512Hex(data.password))
                        .set("user_role", "USER")
                        .set("user_state", false)
                        .set("is_Public", data.isPublic.equals("on"))
                        .set("occupation", data.occupation)
                        .set("workplace", data.workplace)
                        .set("address", data.address)
                        .set("postal_Code", data.postalCode)
                        .set("NIF", data.NIF)
                        .build();
                txn.add(user);
                txn.commit();
                return Response.ok("sucesso").build();
            }
        } finally {
            if(txn.isActive()) {
                txn.rollback();
            }


        }
    }


}
