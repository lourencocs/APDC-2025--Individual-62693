package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult;

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
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

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
    private static final String FIELD_CC = "user_citizen_card";
    private static final String FIELD_EMPLOYER_NIF ="user_employer_nif";

    private static final String ROLE_ENDUSER = "ENDUSER";
    private static final String ROLE_BACKOFFICE = "BACKOFFICE";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_PARTNER = "PARTNER"
            ;
    private static final String STATE_ACTIVE = "active";
    private static final String PROFILE_PUBLIC = "public";

    private static final String NOT_DEFINED = "NOT DEFINED";

    private static final String OPERATION_NAME = "OP6 - listUsers";

    @POST // Changed to POST
    @Path("/")
    public Response listUsers(@HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        LOG.fine("Attempting to list users.");

        String tokenID = AuthUtil.extractTokenID(authHeader);
        if (tokenID == null) {
            OpResult errorResult = new OpResult(OPERATION_NAME, null, null, "Missing Authorization Bearer token.");
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(errorResult)).build();
        }

        Entity requestingUser = AuthUtil.validateToken(datastore, tokenID);
        if (requestingUser == null) {
            LOG.warning("List users request with invalid or expired token: " + tokenID);
            OpResult errorResult = new OpResult(OPERATION_NAME, null, tokenID, "Invalid or expired token.");
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(errorResult)).build();
        }
        String requesterRole = requestingUser.getString(FIELD_ROLE);
        LOG.info("List users request by user: " + requestingUser.getKey().getName() + " with role: " + requesterRole);

        Query<Entity> query = Query.newEntityQueryBuilder()
                .setKind(KIND_USER)
                .build();

        QueryResults<Entity> results = datastore.run(query);
        List<Map<String, Object>> usersList = new ArrayList<>();

        while (results.hasNext()) {
            Entity targetUser = results.next();
            String targetUsername = targetUser.getKey().getName();
            String targetRole = targetUser.getString(FIELD_ROLE);

            Map<String, Object> userData = null;

            switch (requesterRole.toUpperCase()) {
                case ROLE_ENDUSER:
                    if ((ROLE_ENDUSER.equals(targetRole) || ROLE_PARTNER.equals(targetRole)) &&
                            targetUser.contains(FIELD_PROFILE) && PROFILE_PUBLIC.equalsIgnoreCase(targetUser.getString(FIELD_PROFILE)) &&
                            targetUser.contains(FIELD_STATE) && STATE_ACTIVE.equalsIgnoreCase(targetUser.getString(FIELD_STATE))) {
                        userData = new HashMap<>();
                        userData.put("username", targetUsername);
                        userData.put(FIELD_EMAIL, targetUser.contains(FIELD_EMAIL) ? targetUser.getString(FIELD_EMAIL) : NOT_DEFINED);
                        userData.put("name", targetUser.contains(FIELD_USERNAME_DISPLAY) ? targetUser.getString(FIELD_USERNAME_DISPLAY) : NOT_DEFINED);
                        usersList.add(userData);
                    }
                    break;
                case ROLE_BACKOFFICE:
                    if (ROLE_ENDUSER.equals(targetRole) || ROLE_PARTNER.equals(targetRole)) {
                        userData = entityToFullMap(targetUser, targetUsername);
                        usersList.add(userData);
                    }
                    break;
                case ROLE_ADMIN:
                    userData = entityToFullMap(targetUser, targetUsername);
                    usersList.add(userData);
                    break;
                default:
                    LOG.warning("Unknown role encountered for requester: " + requesterRole);
                    OpResult errorResult = new OpResult(OPERATION_NAME, null, tokenID, "Internal role configuration error.");
                    return Response.status(Status.FORBIDDEN).entity(g.toJson(errorResult)).build();
            }
        }

        LOG.info("Successfully listed " + usersList.size() + " users for role " + requesterRole);
        return Response.ok(g.toJson(usersList)).build();
    }

    private Map<String, Object> entityToFullMap(Entity userEntity, String username) {
        Map<String, Object> map = new HashMap<>();
        map.put("username", username);
        map.put("name", userEntity.contains(FIELD_USERNAME_DISPLAY) ? userEntity.getString(FIELD_USERNAME_DISPLAY) : NOT_DEFINED);
        map.put(FIELD_EMAIL, userEntity.contains(FIELD_EMAIL) ? userEntity.getString(FIELD_EMAIL) : NOT_DEFINED);
        map.put(FIELD_PROFILE, userEntity.contains(FIELD_PROFILE) ? userEntity.getString(FIELD_PROFILE) : NOT_DEFINED);
        map.put(FIELD_PHONE, userEntity.contains(FIELD_PHONE) ? userEntity.getString(FIELD_PHONE) : NOT_DEFINED);
        map.put(FIELD_ROLE, userEntity.contains(FIELD_ROLE) ? userEntity.getString(FIELD_ROLE) : NOT_DEFINED);
        map.put(FIELD_STATE, userEntity.contains(FIELD_STATE) ? userEntity.getString(FIELD_STATE) : NOT_DEFINED);
        map.put(FIELD_IS_PUBLIC, userEntity.contains(FIELD_IS_PUBLIC) ? userEntity.getBoolean(FIELD_IS_PUBLIC) : NOT_DEFINED);
        map.put(FIELD_OCCUPATION, userEntity.contains(FIELD_OCCUPATION) ? userEntity.getString(FIELD_OCCUPATION) : NOT_DEFINED);
        map.put(FIELD_WORKPLACE, userEntity.contains(FIELD_WORKPLACE) ? userEntity.getString(FIELD_WORKPLACE) : NOT_DEFINED);
        map.put(FIELD_ADDRESS, userEntity.contains(FIELD_ADDRESS) ? userEntity.getString(FIELD_ADDRESS) : NOT_DEFINED);
        map.put(FIELD_POSTAL_CODE, userEntity.contains(FIELD_POSTAL_CODE) ? userEntity.getString(FIELD_POSTAL_CODE) : NOT_DEFINED);
        map.put(FIELD_NIF, userEntity.contains(FIELD_NIF) ? userEntity.getString(FIELD_NIF) : NOT_DEFINED);
        map.put(FIELD_CC, userEntity.contains(FIELD_CC) ? userEntity.getString(FIELD_CC) : NOT_DEFINED);
        map.put(FIELD_EMPLOYER_NIF, userEntity.contains(FIELD_EMPLOYER_NIF) ? userEntity.getString(FIELD_EMPLOYER_NIF) : NOT_DEFINED);
        return map;
    }
}