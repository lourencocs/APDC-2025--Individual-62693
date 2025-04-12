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
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult;

import java.util.logging.Logger;


@Path("/login")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LoginResource {

    private static final String OPERATION_NAME = "OP8 - login";

    private final Gson g = new Gson();
    private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();
    private final KeyFactory userKeyFactory = datastore.newKeyFactory().setKind("User");

    private static final String FIELD_PASSWORD = "user_pwd";
    private static final String FIELD_STATE = "user_state";
    private static final String FIELD_ROLE = "user_role";

    public LoginResource() {
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doLogin(LoginData data, @Context HttpServletRequest request, @Context HttpHeaders headers) {
        LOG.fine("Attempt to login user: " + data.userID);

        if (data.userID == null || data.password == null || data.userID.isEmpty() || data.password.isEmpty()) {
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Missing userID or password.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }

        Key userKey = userKeyFactory.newKey(data.userID);
        Key ctrsKey = datastore.newKeyFactory()
                .addAncestor(PathElement.of("User", data.userID))
                .setKind("UserStats").newKey("counters");

        Transaction txn = datastore.newTransaction();
        try {
            Entity user = txn.get(userKey);
            if (user == null) {
                LOG.warning("Failed login attempt for non-existent user: " + data.userID);
                OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Incorrect userID or password.");
                return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
            }

            Entity stats = txn.get(ctrsKey);
            if (stats == null) {
                stats = Entity.newBuilder(ctrsKey)
                        .set("user_stats_logins", 0L)
                        .set("user_stats_failed", 0L)
                        .set("user_first_login", Timestamp.now())
                        .set("user_last_login", Timestamp.now())
                        .build();
            }

            String storedPasswordHash = user.getString(FIELD_PASSWORD);
            boolean isActive = user.contains(FIELD_STATE) && user.getBoolean(FIELD_STATE);

            if (storedPasswordHash.equals(DigestUtils.sha512Hex(data.password))) {
                if (isActive) {
                    LOG.info("User '" + data.userID + "' credentials verified.");

                    String userRole = user.getString(FIELD_ROLE);
                    AuthToken token = new AuthToken(data.userID, userRole);

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
                            .set("user_stats_logins", 1L + stats.getLong("user_stats_logins"))
                            .set("user_stats_failed", 0L)
                            .set("user_last_login", Timestamp.now())
                            .build();

                    Key logKey = datastore.allocateId(
                            datastore.newKeyFactory()
                                    .addAncestor(PathElement.of("User", data.userID))
                                    .setKind("UserLog").newKey()
                    );

                    Entity.Builder logBuilder = Entity.newBuilder(logKey)
                            .set("user_login_ip", request.getRemoteAddr())
                            .set("user_login_host", request.getRemoteHost())
                            .set("user_login_time", Timestamp.now())
                            .set("user_associated_token", token.tokenID);

                    String latLon = headers.getHeaderString("X-AppEngine-CityLatLong");
                    if (latLon != null) {
                        logBuilder.set("user_login_latlon", StringValue.newBuilder(latLon).setExcludeFromIndexes(true).build());
                    }

                    String city = headers.getHeaderString("X-AppEngine-City");
                    if (city != null) {
                        logBuilder.set("user_login_city", city);
                    }

                    String country = headers.getHeaderString("X-AppEngine-Country");
                    if (country != null) {
                        logBuilder.set("user_login_country", country);
                    }

                    Entity log = logBuilder.build();

                    txn.put(tokenEntity, log, ustats);
                    txn.commit();

                    LOG.info("User '" + data.userID + "' logged in successfully. Token created: " + token.tokenID);
                    OpResult successResult = new OpResult(OPERATION_NAME, data, g.toJson(token), "Login successful.");
                    return Response.ok(g.toJson(successResult)).build();

                } else {
                    LOG.warning("Login attempt failed for inactive user: " + data.userID);
                    txn.rollback();
                    OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Account is inactive.");
                    return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
                }
            } else {
                LOG.warning("Failed login attempt for user: " + data.userID + " (Incorrect Password)");

                Entity ustats = Entity.newBuilder(stats)
                        .set("user_stats_failed", 1L + stats.getLong("user_stats_failed"))
                        .set("user_last_attempt", Timestamp.now())
                        .set("user_stats_logins", stats.getLong("user_stats_logins"))
                        .set("user_first_login", stats.getTimestamp("user_first_login"))
                        .set("user_last_login", stats.getTimestamp("user_last_login"))
                        .build();
                txn.put(ustats);
                txn.commit();

                OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Incorrect userID or password.");
                return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
            }
        } catch (Exception e) {
            LOG.severe("Login transaction failed: " + e.getMessage());
            e.printStackTrace();
            if (txn.isActive()) {
                txn.rollback();
            }
            OpResult errorResult = new OpResult(OPERATION_NAME, data, null, "Login failed due to server error.");
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(errorResult)).build();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }
}