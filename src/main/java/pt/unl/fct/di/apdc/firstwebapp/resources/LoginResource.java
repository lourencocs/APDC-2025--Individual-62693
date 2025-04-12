package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
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
import pt.unl.fct.di.apdc.firstwebapp.util.LoginData;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil; // Import AuthUtil

import java.util.logging.Logger;

@Path("/login")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LoginResource {

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
    // Consistent Project ID
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");

    // Consistent Field Names (Consider defining these in a shared constants class)
    private static final String FIELD_PASSWORD = "user_pwd";
    private static final String FIELD_STATE = "user_state";
    private static final String FIELD_ROLE = "user_role";

    public LoginResource() { }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLogin(LoginData data, @Context HttpServletRequest request, @Context HttpHeaders headers) {
        LOG.fine("Attempt to login user: " + data.username);

        if (data.username == null || data.password == null || data.username.isEmpty() || data.password.isEmpty()) {
            return Response.status(Status.BAD_REQUEST).entity("Missing username or password.").build();
        }

        Key userKey = userKeyFactory.newKey(data.username);
        Key ctrsKey = datastore.newKeyFactory()
                .addAncestor(PathElement.of("User", data.username))
                .setKind("UserStats").newKey("counters");
        // Key logKey = datastore.allocateId(...) // Keep log key generation if needed

        Transaction txn = datastore.newTransaction();
        try {
            Entity user = txn.get(userKey);
            // User existence check
            if (user == null) {
                LOG.warning("Failed login attempt for non-existent user: " + data.username);
                // Do not update stats for non-existent users to prevent enumeration attacks
                // txn.rollback(); // No need to rollback if nothing was done
                return Response.status(Status.FORBIDDEN).entity("Incorrect username or password.").build(); // Generic message
            }

            // Fetch stats - must exist or be created within the transaction
            Entity stats = txn.get(ctrsKey);
            if (stats == null) {
                stats = Entity.newBuilder(ctrsKey)
                        .set("user_stats_logins", 0L)
                        .set("user_stats_failed", 0L)
                        .set("user_first_login", Timestamp.now()) // Set on first actual interaction
                        .set("user_last_login", Timestamp.now())
                        .build();
            }

            String storedPasswordHash = user.getString(FIELD_PASSWORD);
            boolean isActive = user.contains(FIELD_STATE) && user.getBoolean(FIELD_STATE); // Check if state exists

            // Password verification
            if (storedPasswordHash.equals(DigestUtils.sha512Hex(data.password))) {
                // Account state check
                if (isActive) {
                    // SUCCESSFUL LOGIN
                    LOG.info("User '" + data.username + "' credentials verified.");

                    // Create AuthToken object (includes role, verifier, etc.)
                    String userRole = user.getString(FIELD_ROLE);
                    AuthToken token = new AuthToken(data.username, userRole);

                    // Create AuthToken entity to store in Datastore
                    Key tokenKey = datastore.newKeyFactory().setKind(AuthUtil.AUTH_TOKEN_KIND).newKey(token.tokenID);
                    Entity tokenEntity = Entity.newBuilder(tokenKey)
                            .set("user_username", token.username)
                            .set("user_role", token.role)
                            .set("creation_date", token.creationData)
                            .set("expiration_date", token.expirationData)
                            .set("verifier", token.verifier)
                            // Optionally store IP/User-Agent used to create the token for security auditing
                            .set("creation_ip", StringValue.newBuilder(request.getRemoteAddr()).setExcludeFromIndexes(true).build())
                            .build();

                    // Update user statistics for successful login
                    Entity ustats = Entity.newBuilder(stats) // Build from existing stats
                            .set("user_stats_logins", 1L + stats.getLong("user_stats_logins"))
                            // Reset failed attempts on successful login
                            .set("user_stats_failed", 0L)
                            .set("user_last_login", Timestamp.now())
                            .build();

                    // Generate Log Key dynamically inside transaction if needed
                    Key logKey = datastore.allocateId(
                            datastore.newKeyFactory()
                                    .addAncestor(PathElement.of("User", data.username))
                                    .setKind("UserLog").newKey()
                    );
                    Entity log = Entity.newBuilder(logKey)
                            .set("user_login_ip", request.getRemoteAddr())
                            .set("user_login_host", request.getRemoteHost())
                            .set("user_login_latlon", StringValue.newBuilder(headers.getHeaderString("X-AppEngine-CityLatLong")).setExcludeFromIndexes(true).build())
                            .set("user_login_city", headers.getHeaderString("X-AppEngine-City"))
                            .set("user_login_country", headers.getHeaderString("X-AppEngine-Country"))
                            .set("user_login_time", Timestamp.now())
                            .set("user_associated_token", token.tokenID) // Link log entry to token
                            .build();


                    // Batch update: store token, log, and stats
                    txn.put(tokenEntity, log, ustats);
                    txn.commit();

                    LOG.info("User '" + data.username + "' logged in successfully. Token created: " + token.tokenID);
                    // Return the AuthToken object (contains all required fields)
                    return Response.ok(g.toJson(token)).build();

                } else {
                    // Account is inactive
                    LOG.warning("Login attempt failed for inactive user: " + data.username);
                    // Do NOT update failed login count here, as password was correct
                    txn.rollback();
                    return Response.status(Status.FORBIDDEN).entity("Account is inactive.").build();
                }
            } else {
                // Incorrect password
                LOG.warning("Failed login attempt for user: " + data.username + " (Incorrect Password)");

                // Update stats for failed attempt
                Entity ustats = Entity.newBuilder(stats)
                        .set("user_stats_failed", 1L + stats.getLong("user_stats_failed"))
                        .set("user_last_attempt", Timestamp.now()) // Record time of failed attempt
                        // Do not update successful login count or last login time
                        .set("user_stats_logins", stats.getLong("user_stats_logins"))
                        .set("user_first_login", stats.getTimestamp("user_first_login"))
                        .set("user_last_login", stats.getTimestamp("user_last_login"))
                        .build();
                txn.put(ustats);
                txn.commit();

                return Response.status(Status.FORBIDDEN).entity("Incorrect username or password.").build(); // Generic message
            }
        } catch (Exception e) {
            LOG.severe("Login transaction failed: " + e.getMessage());
            if (txn.isActive()) {
                txn.rollback();
            }
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Login failed due to server error.").build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}