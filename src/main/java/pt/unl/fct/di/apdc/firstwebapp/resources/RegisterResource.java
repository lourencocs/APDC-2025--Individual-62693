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
    private static final String FIELD_CC = "user_citizen_card";
    private static final String FIELD_EMPLOYER_NIF ="user_employer_nif";

    private static final String DEFAULT_ROLE = "ENDUSER";
    private static final String DEFAULT_STATE = "inactive";

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRegister(RegisterData data) {
        LOG.fine("Register attempt by user: " + data.userID);

        if (!data.validRegistration()) {
            OpResult result = new OpResult(OPERATION_NAME, data, null, "Missing, wrong or empty required parameter (userID," +
                    " name, email, phone, password, confirmationPassword, profile).");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(result)).build();
        }
        if (!data.password.equals(data.passwordConfirmation)) {
            OpResult result = new OpResult(OPERATION_NAME, data, null, "Passwords do not match.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(result)).build();
        }
        if (!isValidEmail(data.email)) {
            OpResult result = new OpResult(OPERATION_NAME, data, null, "Invalid email format.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(result)).build();
        }
        if (!isValidPassword(data.password)) {
            OpResult result = new OpResult(OPERATION_NAME, data, null, "Password does not meet complexity requirements (e.g., minimum 8 chars, letters, numbers).");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(result)).build();
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
                        .set(FIELD_OCCUPATION, data.occupation != null ? data.occupation : "NOT DEFINED")
                        .set(FIELD_WORKPLACE, data.workplace != null ? data.workplace : "NOT DEFINED")
                        .set(FIELD_ADDRESS, data.address != null ? data.address : "NOT DEFINED")
                        .set(FIELD_POSTAL_CODE, data.postalCode != null ? data.postalCode : "NOT DEFINED")
                        .set(FIELD_NIF, data.NIF != null ? data.NIF : "NOT DEFINED")
                        .set(FIELD_CC, data.citizenCardNumber != null ? data.citizenCardNumber : "NOT DEFINED")
                        .set(FIELD_EMPLOYER_NIF, data.employerNIF != null ? data.employerNIF : "NOT DEFINED")
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
     * --- UPDATED Password Validation ---
     * Checks if the password meets the complexity requirements:
     * - At least one digit [0-9]
     * - At least one lowercase letter [a-z]
     * - At least one uppercase letter [A-Z]
     * - At least one punctuation symbol (e.g., !@#$%^&*()_+=.,<>?;':"{}[]|\-)
     * - Minimum length (e.g., 8 characters - adjustable)
     * @param password password input
     * @return true if it passes the requirements, false otherwise.
     */
    private boolean isValidPassword(String password) {
        if (password == null) return false;

        // Define the required character classes
        boolean hasDigit = password.matches(".*[0-9].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasUpper = password.matches(".*[A-Z].*");
        // Define punctuation based on requirement "sinais de pontuação". Adjust the characters inside [] if needed.
        // Common punctuation: !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~
        // Let's use a representative set. Escape special regex characters like \ - [ ] ^
        boolean hasPunctuation = password.matches(".*[!@#$%^&*()_+=.,<>?;'].*");

        int minLength = 8;
        boolean hasMinLength = password.length() >= minLength;

        return hasDigit && hasLower && hasUpper && hasPunctuation && hasMinLength;

    }
}