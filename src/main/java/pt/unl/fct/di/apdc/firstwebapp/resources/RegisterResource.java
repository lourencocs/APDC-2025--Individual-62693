package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.RegisterData;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@Path("/register")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class RegisterResource {

    private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());

    private final Gson g = new Gson();
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegister(RegisterData data) {
        LOG.fine("Register attempt by user: " + data.username);

        if (!data.validRegistration()) {
            return Response.status(Status.BAD_REQUEST).entity("Missing or wrong parameter.").build();
        }

        // Validate email format
        if (!isValidEmail(data.email)) {
            return Response.status(Status.BAD_REQUEST).entity("Invalid email format.").build();
        }

        // Validate password strength (example: minimum 8 characters, letters and numbers)
        if (!isValidPassword(data.password)) {
            return Response.status(Status.BAD_REQUEST).entity("Invalid password format.").build();
        }

        Transaction txn = datastore.newTransaction();

        try {
            Key userKey = datastore.newKeyFactory().setKind("User").newKey(data.username); // Correct KeyFactory usage
            Entity user = txn.get(userKey);
            if (user != null) {
                txn.rollback();
                return Response.status(Status.CONFLICT).entity("User already exists.").build();
            } else {
                user = Entity.newBuilder(userKey)
                        .set("user_name", data.username)
                        .set("user_email", data.email)
                        .set("user_profile", data.profile != null ? data.profile : "") // Handle null profile
                        .set("user_phone", data.phone)
                        .set("user_pwd", DigestUtils.sha512Hex(data.password))
                        .set("user_role", "USER")
                        .set("user_state", false)
                        .set("user_isPublic", data.isPublic)
                        .set("occupation", data.occupation != null ? data.occupation : "") // Handle null occupation
                        .set("workplace", data.workplace != null ? data.workplace : "") // Handle null workplace
                        .set("address", data.address != null ? data.address : "") // Handle null address
                        .set("postal_Code", data.postalCode != null ? data.postalCode : "") // Handle null postalCode
                        .set("NIF", data.NIF != null ? data.NIF : "") // Handle null NIF
                        .build();
                txn.add(user);
                txn.commit();
                return Response.ok("sucesso").build();
            }
        } catch (Exception e) {
            LOG.severe("Registration failed: " + e.toString());
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Server error: " + e.getMessage()).build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        Pattern pattern = Pattern.compile(emailRegex);
        return pattern.matcher(email).matches();
    }

    private boolean isValidPassword(String password) {
        // Example: Minimum 8 characters, at least one letter and one number
        String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$";
        Pattern pattern = Pattern.compile(passwordRegex);
        return pattern.matcher(password).matches();
    }
}