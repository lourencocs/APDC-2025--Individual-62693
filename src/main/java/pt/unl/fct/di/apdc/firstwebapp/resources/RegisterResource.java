package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult;
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

    private static final String OPERATION_NAME = "OP1 - register";

    private static final Logger LOG = Logger.getLogger(RegisterResource.class.getName());
    private final Gson g = new Gson();
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

    private static final String KIND_USER = "User";
    private static final String FIELD_EMAIL = "user_email";
    private static final String FIELD_NAME = "user_name";
    private static final String FIELD_PHONE = "user_phone";
    private static final String FIELD_PASSWORD = "user_pwd";
    private static final String FIELD_ROLE = "user_role";
    private static final String FIELD_STATE = "user_state";
    private static final String FIELD_PROFILE = "user_profile";
    private static final String FIELD_OCCUPATION = "occupation";
    private static final String FIELD_WORKPLACE = "workplace";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_POSTAL_CODE = "postal_Code";
    private static final String FIELD_NIF = "NIF";

    private static final String DEFAULT_ROLE = "USER";
    private static final boolean DEFAULT_STATE = false;

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegister(RegisterData data) {
        LOG.fine("Register attempt by user: " + data.userID);

        if (!data.validRegistration()) {
            return Response.status(Status.BAD_REQUEST).entity("Missing or empty required parameter (userID, name, email, phone, password).").build();
        }
        if (!isValidEmail(data.email)) {
            return Response.status(Status.BAD_REQUEST).entity("Invalid email format.").build();
        }
        if (!isValidPassword(data.password)) {
            return Response.status(Status.BAD_REQUEST).entity("Password does not meet complexity requirements (e.g., minimum 8 chars, letters, numbers).").build();
        }

        Transaction txn = datastore.newTransaction();
        try {
            Key userKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(data.userID);
            Entity user = txn.get(userKey);

            if (user != null) {
                txn.rollback();
                LOG.warning("Registration conflict: User '" + data.userID + "' already exists.");
                return Response.status(Status.CONFLICT).entity("UserID already exists.").build();
            } else {
                user = Entity.newBuilder(userKey)
                        .set(FIELD_NAME, data.name)
                        .set(FIELD_EMAIL, data.email)
                        .set(FIELD_PHONE, data.phone)
                        .set(FIELD_PASSWORD, DigestUtils.sha512Hex(data.password))
                        .set(FIELD_ROLE, DEFAULT_ROLE)
                        .set(FIELD_STATE, DEFAULT_STATE)
                        .set(FIELD_PROFILE, data.profile != null ? data.profile : "public")
                        .set(FIELD_OCCUPATION, data.occupation != null ? data.occupation : "")
                        .set(FIELD_WORKPLACE, data.workplace != null ? data.workplace : "")
                        .set(FIELD_ADDRESS, data.address != null ? data.address : "")
                        .set(FIELD_POSTAL_CODE, data.postalCode != null ? data.postalCode : "")
                        .set(FIELD_NIF, data.NIF != null ? data.NIF : "")
                        .set("user_creation_time", Timestamp.now())
                        .build();

                Key ctrsKey = datastore.newKeyFactory()
                        .addAncestor(PathElement.of(KIND_USER, data.userID))
                        .setKind("UserStats").newKey("counters");
                Entity stats = Entity.newBuilder(ctrsKey)
                        .set("user_stats_logins", 0L)
                        .set("user_stats_failed", 0L)
                        .build();

                txn.put(user, stats);
                txn.commit();

                OpResult result = new OpResult(OPERATION_NAME, data, null, "User registered successfully.");
                return Response.status(Status.CREATED).entity(g.toJson(result)).build();
            }
        } catch (Exception e) {
            LOG.severe("Registration failed: " + e.getMessage());
            if (txn.isActive()) txn.rollback();
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Server error during registration.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } finally {
            if (txn.isActive()) txn.rollback();
        }
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return Pattern.matches(emailRegex, email);
    }

    /**
     * Password has at least 1 number, 1 lowercase and 1 uppercase letter.
     * @param password password input
     * @return true if it passes the requirements, false otherwise.
     */
    private boolean isValidPassword(String password) {
        if (password == null) return false;
        String passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$";
        return Pattern.matches(passwordRegex, password);
    }
}