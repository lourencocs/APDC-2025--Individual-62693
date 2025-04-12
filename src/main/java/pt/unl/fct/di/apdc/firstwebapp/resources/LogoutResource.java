package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;

import java.util.logging.Logger;

@Path("/logout")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LogoutResource {

    private static final Logger LOG = Logger.getLogger(LogoutResource.class.getName());
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

    @POST
    @Path("/")
    public Response doLogout(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        String tokenID = AuthUtil.extractTokenID(authHeader);
        LOG.fine("Attempting to logout token: " + tokenID);

        if (tokenID == null) {
            // Don't reveal if token was malformed vs non-existent
            LOG.warning("Logout attempt with missing or malformed token.");
            return Response.ok().entity("Logout successful (or token invalid).").build(); // Return OK even if token missing/invalid
        }

        Key tokenKey = datastore.newKeyFactory().setKind(AuthUtil.AUTH_TOKEN_KIND).newKey(tokenID);

        try {
            // Check if token exists before deleting (optional, delete is idempotent)
            Entity tokenEntity = datastore.get(tokenKey);
            if (tokenEntity != null) {
                datastore.delete(tokenKey);
                LOG.info("Successfully logged out and revoked token: " + tokenID + " for user " + tokenEntity.getString("user_username"));
                return Response.ok().entity("Logout successful.").build();
            } else {
                LOG.warning("Logout attempt for non-existent token: " + tokenID);
                return Response.ok().entity("Logout successful (or token invalid).").build(); // Token already gone or never existed
            }
        } catch (Exception e) {
            LOG.severe("Error during logout for token " + tokenID + ": " + e.getMessage());
            // Still return OK to the client, as the goal is to end the session state on the client side
            // But log the server error
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Logout failed on server, but proceed.").build(); // Or just OK
        }
    }
}
