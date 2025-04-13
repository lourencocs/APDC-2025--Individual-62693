package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter; // Import PropertyFilter
import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.apache.commons.codec.digest.DigestUtils;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthToken;
import pt.unl.fct.di.apdc.firstwebapp.util.LoginData; // Use updated LoginData
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern; // Import Pattern for email check


@Path("/login")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LoginResource {

    private static final String OPERATION_NAME = "OP2 - login"; // Correct name

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User"); // Keep for key-based lookup

    private static final String KIND_USER = "User"; // User Kind constant
    private static final String FIELD_PASSWORD = "user_pwd";
    private static final String FIELD_STATE = "user_state";
    private static final String FIELD_ROLE = "user_role";
    private static final String FIELD_EMAIL = "user_email"; // Email field constant
    private static final String ACTIVE_STATE = "active";

    // Simple regex for basic email format check
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

    public LoginResource() {
    }

    // Helper method to check if the identifier looks like an email
    private boolean isEmailFormat(String identifier) {
        return identifier != null && EMAIL_PATTERN.matcher(identifier).matches();
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLogin(LoginData data, @Context HttpServletRequest request, @Context HttpHeaders headers) {
        // Use the identifier from updated LoginData
        LOG.fine("Attempt to login user with identifier: " + data.identifier);

        // Validate basic input using the updated LoginData
        if (!data.isValid()) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing identifier or password.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }

        Transaction txn = datastore.newTransaction();
        try {
            Entity userEntity = null; // Variable to hold the found user entity
            String identifierUsed = data.identifier; // Keep track for logging/errors

            // --- Logic to find user by Email or UserID ---
            if (isEmailFormat(identifierUsed)) {
                LOG.fine("Identifier detected as email: " + identifierUsed);
                // Query by email (ensure user_email is indexed)
                // Consider converting email to lowercase here and storing emails lowercase during registration for case-insensitive login
                Query<Entity> query = Query.newEntityQueryBuilder()
                        .setKind(KIND_USER)
                        .setFilter(PropertyFilter.eq(FIELD_EMAIL, identifierUsed))
                        .setLimit(1) // Expect only one user per email
                        .build();

                QueryResults<Entity> results = txn.run(query);

                if (results.hasNext()) {
                    userEntity = results.next();
                    LOG.fine("User found via email query for: " + identifierUsed);
                } else {
                    LOG.warning("No user found for email: " + identifierUsed);
                }

            } else {
                LOG.fine("Identifier assumed to be userID: " + identifierUsed);
                Key userKey = userKeyFactory.newKey(identifierUsed);
                userEntity = txn.get(userKey);

                if (userEntity != null) {
                    LOG.fine("User found via key lookup for: " + identifierUsed);
                } else {
                    LOG.warning("No user found for userID: " + identifierUsed);
                }
            }

            if (userEntity == null) {
                LOG.warning("Login failed: No user found for identifier: " + identifierUsed);
                txn.rollback();
                OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Incorrect identifier or password.");
                return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
            }

            String actualUserID = userEntity.getKey().getName();
            LOG.fine("Processing login checks for user: " + actualUserID + " (found via: " + identifierUsed + ")");

            String storedPasswordHash = userEntity.getString(FIELD_PASSWORD);
            String currentState = userEntity.contains(FIELD_STATE) ? userEntity.getString(FIELD_STATE) : "";
            boolean isActive = ACTIVE_STATE.equalsIgnoreCase(currentState);

            Key ctrsKey = datastore.newKeyFactory()
                    .addAncestor(PathElement.of(KIND_USER, actualUserID)) //
                    .setKind("UserStats").newKey("counters");
            Entity stats = txn.get(ctrsKey);

            if (stats == null) {
                LOG.warning("Stats entity missing for user: " + actualUserID + ". Creating default.");
                stats = Entity.newBuilder(ctrsKey)
                        .set("user_stats_logins", 0L)
                        .set("user_stats_failed", 0L)
                        .set("user_stats_creation_time", Timestamp.now())
                        .build();
            }

            LOG.warning("Entered password hash: " + DigestUtils.sha512Hex(data.password));
            LOG.warning("Stored password hash: " + storedPasswordHash);

            if (storedPasswordHash.equals(DigestUtils.sha512Hex(data.password))) {
                if (isActive) {
                    LOG.info("User '" + actualUserID + "' credentials verified and account is active.");

                    String userRole = userEntity.getString(FIELD_ROLE);
                    AuthToken token = new AuthToken(actualUserID, userRole);

                    Key tokenKey = datastore.newKeyFactory().setKind(AuthUtil.AUTH_TOKEN_KIND).newKey(token.tokenID);
                    Entity tokenEntity = Entity.newBuilder(tokenKey)
                            .set("user_username", token.username)
                            .set("user_role", token.role)
                            .set("creation_date", token.creationData)
                            .set("expiration_date", token.expirationData)
                            .set("verifier", token.verifier)
                            .set("creation_ip", StringValue.newBuilder(request.getRemoteAddr()).setExcludeFromIndexes(true).build())
                            .build();

                    Entity ustats = Entity.newBuilder(stats)
                            .set("user_stats_logins", 1L + (stats.contains("user_stats_logins") ? stats.getLong("user_stats_logins") : 0L))
                            .set("user_stats_failed", 0L) // Reset failed attempts
                            .set("user_last_login", Timestamp.now())
                            .set("user_first_login", stats.contains("user_first_login") ? stats.getTimestamp("user_first_login") : Timestamp.now())
                            .set("user_last_attempt", stats.contains("user_last_attempt") ? stats.getTimestamp("user_last_attempt") : Timestamp.now())
                            .build();

                    Key logKey = datastore.allocateId(
                            datastore.newKeyFactory()
                                    .addAncestor(PathElement.of(KIND_USER, actualUserID)) // Use actualUserID
                                    .setKind("UserLog").newKey()
                    );

                    Entity.Builder logBuilder = Entity.newBuilder(logKey)
                            .set("user_login_ip", request.getRemoteAddr())
                            .set("user_login_host", request.getRemoteHost())
                            .set("user_login_time", Timestamp.now())
                            .set("user_associated_token", token.tokenID);
                    String latLon = headers.getHeaderString("X-AppEngine-CityLatLong");
                    if (latLon != null) logBuilder.set("user_login_latlon", StringValue.newBuilder(latLon).setExcludeFromIndexes(true).build());
                    String city = headers.getHeaderString("X-AppEngine-City");
                    if (city != null) logBuilder.set("user_login_city", city);
                    String country = headers.getHeaderString("X-AppEngine-Country");
                    if (country != null) logBuilder.set("user_login_country", country);
                    Entity log = logBuilder.build();

                    txn.put(tokenEntity, ustats, log);
                    txn.commit();

                    LOG.info("User '" + actualUserID + "' logged in successfully. Token created: " + token.tokenID);
                    OpResult successResult = new OpResult(OPERATION_NAME, data, g.toJson(token), "Login successful.");
                    return Response.ok(g.toJson(successResult)).build();

                } else {
                    LOG.warning("Login attempt failed for user: " + actualUserID + ". Account is not active (State: '" + currentState + "').");
                    txn.rollback();
                    OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Account is inactive or suspended.");
                    return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
                }
            } else {
                LOG.warning("Failed login attempt for user: " + actualUserID + " (Incorrect Password)");

                Entity ustats = Entity.newBuilder(stats)
                        .set("user_stats_failed", 1L + (stats.contains("user_stats_failed") ? stats.getLong("user_stats_failed") : 0L))
                        .set("user_last_attempt", Timestamp.now())
                        .set("user_stats_logins", stats.contains("user_stats_logins") ? stats.getLong("user_stats_logins") : 0L)
                        .set("user_first_login", stats.contains("user_first_login") ? stats.getTimestamp("user_first_login") : Timestamp.now())
                        .set("user_last_login", stats.contains("user_last_login") ? stats.getTimestamp("user_last_login") : Timestamp.now())
                        .build();
                txn.put(ustats);
                txn.commit();

                OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Incorrect identifier or password.");
                return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Login transaction failed for identifier: " + data.identifier, e);
            if (txn != null && txn.isActive()) {
                try {
                    txn.rollback();
                } catch (DatastoreException dse) {
                    LOG.log(Level.SEVERE, "Rollback failed for identifier: " + data.identifier, dse);
                }
            }
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Login failed due to server error.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } finally {
            if (txn != null && txn.isActive()) {
                txn.rollback();
                LOG.warning("Transaction rolled back in finally block for identifier: " + data.identifier);
            }
        }
    }
}