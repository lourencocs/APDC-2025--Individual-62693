package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthToken;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult;

import java.util.logging.Logger;

@Path("/token")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ShowTokenResource {

    private static final String OPERATION_NAME = "OP9 - token";

    private static final Logger LOG = Logger.getLogger(ShowTokenResource.class.getName());
    private final Gson g = new Gson();
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

    @POST
    @Path("/show")
    public Response showTokenDetails(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        String tokenID = AuthUtil.extractTokenID(authHeader);
        LOG.fine("Attempting to show details for token: " + tokenID);

        if (tokenID == null) {
            OpResult errorResult = new OpResult(OPERATION_NAME, null, null, "Missing Authorization Bearer token.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }

        Key tokenKey = datastore.newKeyFactory().setKind(AuthUtil.AUTH_TOKEN_KIND).newKey(tokenID);
        Entity tokenEntity = datastore.get(tokenKey);

        if (tokenEntity == null) {
            LOG.warning("Show token failed: Token not found - " + tokenID);
            OpResult errorResult = new OpResult(OPERATION_NAME, null, null, "Token not found.");
            return Response.status(Status.NOT_FOUND).entity(g.toJson(errorResult)).build();
        }

        long expirationDate = tokenEntity.getLong("expiration_date");
        if (expirationDate < System.currentTimeMillis()) {
            LOG.warning("Show token failed: Token expired - " + tokenID);
            OpResult errorResult = new OpResult(OPERATION_NAME, null, null, "Token expired.");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
        }

        // Construct the AuthToken object from the entity data
        AuthToken tokenData = new AuthToken(
                tokenEntity.getString("user_username"),
                tokenID,
                tokenEntity.getString("user_role"),
                tokenEntity.getLong("creation_date"),
                expirationDate,
                tokenEntity.getString("verifier")
        );

        LOG.info("Successfully retrieved details for token: " + tokenID);
        OpResult successResult = new OpResult(OPERATION_NAME, tokenData, tokenID, tokenData);
        return Response.ok(g.toJson(successResult)).build();
    }
}