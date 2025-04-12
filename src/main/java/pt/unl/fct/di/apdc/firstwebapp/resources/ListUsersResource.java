package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
// Remove ListData import if no longer needed
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil; // Use AuthUtil for validation

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Path("/listusers")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class ListUsersResource {

    private static final Logger LOG = Logger.getLogger(ListUsersResource.class.getName());
    private final Gson g = new Gson();
    // Consistent Project ID
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

    // Consistent Field Names & Roles
    private static final String KIND_USER = "User";
    private static final String FIELD_EMAIL = "user_email";
    private static final String FIELD_USERNAME_DISPLAY = "user_name";
    private static final String FIELD_ROLE = "user_role";
    private static final String FIELD_STATE = "user_state";
    private static final String FIELD_IS_PUBLIC = "user_isPublic";
    private static final String FIELD_PROFILE = "user_profile";
    private static final String FIELD_PHONE = "user_phone";
    private static final String FIELD_OCCUPATION = "occupation";
    private static final String FIELD_WORKPLACE = "workplace";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_POSTAL_CODE = "postal_Code";
    private static final String FIELD_NIF = "NIF";

    private static final String ROLE_USER = "USER";
    private static final String ROLE_GBO = "GBO";
    private static final String ROLE_GA = "GA";
    private static final String ROLE_SU = "SU";
    private static final boolean STATE_ACTIVE = true; // Assuming true means ACTIVE

    // Removed the placeholder token validation method


    // Changed to GET, expecting token in header
    @GET
    @Path("/")
    public Response listUsers(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        LOG.fine("Attempting to list users.");

        String tokenID = AuthUtil.extractTokenID(authHeader);
        if (tokenID == null) {
            return Response.status(Status.BAD_REQUEST).entity("Missing Authorization Bearer token.").build();
        }

        // --- Authentication and Authorization ---
        Entity requestingUser = AuthUtil.validateToken(datastore, tokenID);
        if (requestingUser == null) {
            LOG.warning("List users request with invalid or expired token: " + tokenID);
            // If token is invalid/expired, return 401 Unauthorized
            return Response.status(Status.UNAUTHORIZED).entity("Invalid or expired token.").build();
        }
        String requesterRole = requestingUser.getString(FIELD_ROLE);
        LOG.info("List users request by user: " + requestingUser.getKey().getName() + " with role: " + requesterRole);

        // --- Query Datastore for Users ---
        Query<Entity> query = Query.newEntityQueryBuilder()
                .setKind(KIND_USER)
                // Add ordering if needed, e.g., .setOrderBy(StructuredQuery.OrderBy.asc(FIELD_USERNAME_DISPLAY))
                .build();

        QueryResults<Entity> results = datastore.run(query); // Run outside transaction for reads
        List<Map<String, Object>> usersList = new ArrayList<>();

        // --- Filter Results Based on Requester's Role ---
        while (results.hasNext()) {
            Entity targetUser = results.next();
            String targetUsername = targetUser.getKey().getName();
            String targetRole = targetUser.getString(FIELD_ROLE);
            boolean targetIsPublic = targetUser.contains(FIELD_IS_PUBLIC) && targetUser.getBoolean(FIELD_IS_PUBLIC);
            boolean targetIsActive = targetUser.contains(FIELD_STATE) && targetUser.getBoolean(FIELD_STATE);

            boolean includeUser = false;
            Map<String, Object> userData = null;

            switch (requesterRole) {
                case ROLE_USER:
                    if (ROLE_USER.equals(targetRole) && targetIsPublic && targetIsActive) {
                        includeUser = true;
                        userData = new HashMap<>();
                        userData.put("username", targetUsername);
                        userData.put(FIELD_EMAIL, targetUser.getString(FIELD_EMAIL));
                        // Assuming 'nome' maps to 'user_name' or username key
                        userData.put("name", targetUser.contains(FIELD_USERNAME_DISPLAY) ? targetUser.getString(FIELD_USERNAME_DISPLAY) : targetUsername );
                    }
                    break;
                case ROLE_GBO:
                    if (ROLE_USER.equals(targetRole)) {
                        includeUser = true;
                        userData = entityToFullMap(targetUser, targetUsername);
                    }
                    break;
                case ROLE_GA:
                    if (ROLE_USER.equals(targetRole) || ROLE_GBO.equals(targetRole)) { // GA sees USER, GBO
                        includeUser = true;
                        userData = entityToFullMap(targetUser, targetUsername);
                    }
                    break;
                case ROLE_SU:
                    // SU sees everyone (USER, GBO, GA, SU)
                    includeUser = true;
                    userData = entityToFullMap(targetUser, targetUsername);
                    break;
                default:
                    LOG.warning("Unknown role encountered for requester: " + requesterRole);
                    // Return Forbidden as the requester's role seems invalid
                    return Response.status(Status.FORBIDDEN).entity("Internal role configuration error.").build();
            }

            if (includeUser && userData != null) {
                usersList.add(userData);
            }
        }

        LOG.info("Successfully listed " + usersList.size() + " users for role " + requesterRole);
        return Response.ok(g.toJson(usersList)).build();
    }

    // Helper method remains largely the same, uses constants
    private Map<String, Object> entityToFullMap(Entity userEntity, String username) {
        Map<String, Object> map = new HashMap<>();
        map.put("username", username); // Key
        if (userEntity.contains(FIELD_USERNAME_DISPLAY)) map.put("name", userEntity.getString(FIELD_USERNAME_DISPLAY));
        else map.put("name", username); // Fallback name to username key

        if (userEntity.contains(FIELD_EMAIL)) map.put(FIELD_EMAIL, userEntity.getString(FIELD_EMAIL));
        if (userEntity.contains(FIELD_PROFILE)) map.put(FIELD_PROFILE, userEntity.getString(FIELD_PROFILE));
        if (userEntity.contains(FIELD_PHONE)) map.put(FIELD_PHONE, userEntity.getString(FIELD_PHONE));
        if (userEntity.contains(FIELD_ROLE)) map.put(FIELD_ROLE, userEntity.getString(FIELD_ROLE));
        if (userEntity.contains(FIELD_STATE)) map.put(FIELD_STATE, userEntity.getBoolean(FIELD_STATE));
        if (userEntity.contains(FIELD_IS_PUBLIC)) map.put(FIELD_IS_PUBLIC, userEntity.getBoolean(FIELD_IS_PUBLIC));
        if (userEntity.contains(FIELD_OCCUPATION)) map.put(FIELD_OCCUPATION, userEntity.getString(FIELD_OCCUPATION));
        if (userEntity.contains(FIELD_WORKPLACE)) map.put(FIELD_WORKPLACE, userEntity.getString(FIELD_WORKPLACE));
        if (userEntity.contains(FIELD_ADDRESS)) map.put(FIELD_ADDRESS, userEntity.getString(FIELD_ADDRESS));
        if (userEntity.contains(FIELD_POSTAL_CODE)) map.put(FIELD_POSTAL_CODE, userEntity.getString(FIELD_POSTAL_CODE));
        if (userEntity.contains(FIELD_NIF)) map.put(FIELD_NIF, userEntity.getString(FIELD_NIF));
        // DO NOT INCLUDE FIELD_PASSWORD
        return map;
    }
}