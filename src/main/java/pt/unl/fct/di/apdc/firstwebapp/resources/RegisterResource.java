package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.Timestamp;
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
    // Consistent Project ID
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

    // Consistent Field Names
    private static final String KIND_USER = "User";
    private static final String FIELD_USERNAME_DISPLAY = "user_name"; // If different from key
    private static final String FIELD_EMAIL = "user_email";
    private static final String FIELD_PROFILE = "user_profile";
    private static final String FIELD_PHONE = "user_phone";
    private static final String FIELD_PASSWORD = "user_pwd";
    private static final String FIELD_ROLE = "user_role";
    private static final String FIELD_STATE = "user_state";
    private static final String FIELD_IS_PUBLIC = "user_isPublic";
    private static final String FIELD_OCCUPATION = "occupation";
    private static final String FIELD_WORKPLACE = "workplace";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_POSTAL_CODE = "postal_Code";
    private static final String FIELD_NIF = "NIF";

    // Constants for default values
    private static final String DEFAULT_ROLE = "USER";
    private static final boolean DEFAULT_STATE = true; // New users start ACTIVE by default

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegister(RegisterData data) {
        LOG.fine("Register attempt by user: " + data.username);

        if (!data.validRegistration()) {
            return Response.status(Status.BAD_REQUEST).entity("Missing or empty required parameter (username, email, phone, password).").build();
        }
        if (!isValidEmail(data.email)) {
            return Response.status(Status.BAD_REQUEST).entity("Invalid email format.").build();
        }
        if (!isValidPassword(data.password)) {
            // Define password rules clearly in the message
            return Response.status(Status.BAD_REQUEST).entity("Password does not meet complexity requirements (e.g., minimum 8 chars, letters, numbers).").build();
        }

        Transaction txn = datastore.newTransaction();
        try {
            Key userKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(data.username);
            Entity user = txn.get(userKey);

            if (user != null) {
                txn.rollback();
                LOG.warning("Registration conflict: User '" + data.username + "' already exists.");
                return Response.status(Status.CONFLICT).entity("Username already exists.").build();
            } else {
                // Check for email uniqueness if required
                 /*
                 Query emailQuery = Query.newEntityQueryBuilder()
                     .setKind(KIND_USER)
                     .setFilter(PropertyFilter.eq(FIELD_EMAIL, data.email))
                     .setLimit(1)
                     .build();
                 QueryResults<Entity> emailResults = txn.run(emailQuery);
                 if (emailResults.hasNext()) {
                      txn.rollback();
                      LOG.warning("Registration conflict: Email '" + data.email + "' already exists.");
                      return Response.status(Status.CONFLICT).entity("Email already registered.").build();
                 }
                 */

                user = Entity.newBuilder(userKey)
                        // Key is username
                        .set(FIELD_USERNAME_DISPLAY, data.username) // Store username as property too if needed for display
                        .set(FIELD_EMAIL, data.email)
                        .set(FIELD_PROFILE, data.profile != null ? data.profile : "")
                        .set(FIELD_PHONE, data.phone)
                        .set(FIELD_PASSWORD, DigestUtils.sha512Hex(data.password))
                        .set(FIELD_ROLE, DEFAULT_ROLE) // Assign default role
                        .set(FIELD_STATE, DEFAULT_STATE) // Assign default state (ACTIVE)
                        .set(FIELD_IS_PUBLIC, data.isPublic) // Use the value from RegisterData
                        .set(FIELD_OCCUPATION, data.occupation != null ? data.occupation : "")
                        .set(FIELD_WORKPLACE, data.workplace != null ? data.workplace : "")
                        .set(FIELD_ADDRESS, data.address != null ? data.address : "")
                        .set(FIELD_POSTAL_CODE, data.postalCode != null ? data.postalCode : "")
                        .set(FIELD_NIF, data.NIF != null ? data.NIF : "")
                        // Add creation timestamp
                        .set("user_creation_time", Timestamp.now())
                        .build();

                // Add initial stats entity if desired (or create on first login)
                Key ctrsKey = datastore.newKeyFactory()
                        .addAncestor(PathElement.of(KIND_USER, data.username))
                        .setKind("UserStats").newKey("counters");
                Entity stats = Entity.newBuilder(ctrsKey)
                        .set("user_stats_logins", 0L)
                        .set("user_stats_failed", 0L)
                        .build();

                txn.put(user, stats); // Store user and initial stats
                txn.commit();

                LOG.info("User '" + data.username + "' registered successfully.");
                // Return 201 Created status with representation or location maybe? Or just OK.
                return Response.status(Status.CREATED).entity("User registered successfully.").build();
            }
        } catch (Exception e) {
            LOG.severe("Registration failed: " + e.getMessage());
            if (txn.isActive()) txn.rollback();
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Server error during registration.").build();
        } finally {
            if (txn.isActive()) txn.rollback();
        }
    }

    // Keep validation helpers
    private boolean isValidEmail(String email) {
        if (email == null) return false;
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        return Pattern.matches(emailRegex, email);
    }

    private boolean isValidPassword(String password) {
        if (password == null) return false;
        // Example: Minimum 8 characters, at least one letter and one number
        String passwordRegex = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$";
        return Pattern.matches(passwordRegex, password);
    }
}