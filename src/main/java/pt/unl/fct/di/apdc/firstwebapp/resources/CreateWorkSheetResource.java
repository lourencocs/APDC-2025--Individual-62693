package pt.unl.fct.di.apdc.firstwebapp.resources;

import com.google.cloud.datastore.*;
import com.google.gson.Gson;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import pt.unl.fct.di.apdc.firstwebapp.util.AuthUtil;
import pt.unl.fct.di.apdc.firstwebapp.util.CreateWorkSheetData;
import pt.unl.fct.di.apdc.firstwebapp.util.OpResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.logging.Logger;

@Path("/worksheet")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class CreateWorkSheetResource {

    private static final Logger LOG = Logger.getLogger(CreateWorkSheetResource.class.getName());
    private final Gson g = new Gson();
    private final Datastore datastore = DatastoreOptions.newBuilder().setProjectId("projetoadc-456513").build().getService();

    private static final String KIND_WORKSHEET = "WorkSheet";
    private static final String KIND_USER = "User";

    // Mandatory attributes
    private static final String FIELD_REFERENCE = "reference";
    private static final String FIELD_DESCRIPTION = "description";
    private static final String FIELD_TARGET_TYPE = "target_type";
    private static final String FIELD_ADJUDICATION_STATUS = "adjudication_status";

    private static final String ADJUDICATED = "ADJUDICADO";
    private static final String NOT_ADJUDICATED = "NÃO ADJUDICADO";
    private static final String PUBLIC_PROPERTY = "Propriedade Pública";
    private static final String PRIVATE_PROPERTY = "Propriedade Privada";

    // Optional attributes (filled upon adjudication)
    private static final String FIELD_ADJUDICATION_DATE = "adjudication_date";
    private static final String FIELD_START_DATE = "start_date";
    private static final String FIELD_END_DATE = "end_date";
    private static final String FIELD_PARTNER_ACCOUNT = "partner_account";
    private static final String FIELD_ADJUDICATING_ENTITY = "adjudicating_entity";
    private static final String FIELD_ENTITY_NIF = "entity_nif";
    private static final String FIELD_WORK_STATUS = "work_status";
    private static final String FIELD_OBSERVATIONS = "observations";

    private static final String WORK_STATUS_NOT_STARTED = "NÃO INICIADO";
    private static final String WORK_STATUS_IN_PROGRESS = "EM CURSO";
    private static final String WORK_STATUS_CONCLUDED = "CONCLUÍDO";

    private static final String ROLE_BACKOFFICE = "BACKOFFICE";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_PARTNER = "PARTNER";

    private static final String OPERATION_NAME_CREATE = "OP10 - createWorkSheet";
    private static final String OPERATION_NAME_UPDATE_ADJUDICATION = "OP10 - updateAdjudication";
    private static final String OPERATION_NAME_UPDATE_WORK_STATUS = "OP10 - updateWorkStatus";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createWorkSheet(CreateWorkSheetData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        String tokenID = AuthUtil.extractTokenID(authHeader);

        if (tokenID == null) {
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(new OpResult(OPERATION_NAME_CREATE, data, null, "Missing authentication token."))).build();
        }

        Entity requestingUser = AuthUtil.validateToken(datastore, tokenID);
        if (requestingUser == null) {
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(new OpResult(OPERATION_NAME_CREATE, data, tokenID, "Invalid or expired token."))).build();
        }

        String userRole = requestingUser.getString("user_role");
        if (!userRole.equals(ROLE_BACKOFFICE) && !userRole.equals(ROLE_ADMIN)) {
            return Response.status(Status.FORBIDDEN).entity(g.toJson(new OpResult(OPERATION_NAME_CREATE, data, tokenID, "Insufficient permissions. Only BACKOFFICE or ADMIN can create work sheets."))).build();
        }

        if (data == null || !data.isValidForCreation()) {
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(new OpResult(OPERATION_NAME_CREATE, data, tokenID, "Invalid or missing mandatory attributes for work sheet creation."))).build();
        }

        Transaction txn = datastore.newTransaction();
        try {
            Key workSheetKey = datastore.newKeyFactory().setKind(KIND_WORKSHEET).newKey(data.reference);
            Entity existingWorkSheet = txn.get(workSheetKey);
            if (existingWorkSheet != null) {
                txn.rollback();
                return Response.status(Status.CONFLICT).entity(g.toJson(new OpResult(OPERATION_NAME_CREATE, data, tokenID, "Work sheet with this reference already exists."))).build();
            }

            Entity.Builder builder = Entity.newBuilder(workSheetKey)
                    .set(FIELD_REFERENCE, data.reference)
                    .set(FIELD_DESCRIPTION, data.description)
                    .set(FIELD_TARGET_TYPE, data.targetType)
                    .set(FIELD_ADJUDICATION_STATUS, NOT_ADJUDICATED); // Initially not adjudicated

            txn.put(builder.build());
            txn.commit();

            LOG.info("Work sheet created with reference: " + data.reference + " by user: " + requestingUser.getKey().getName());
            return Response.ok(g.toJson(new OpResult(OPERATION_NAME_CREATE, data, tokenID, "Work sheet created successfully."))).build();

        } catch (Exception e) {
            if (txn.isActive()) {
                txn.rollback();
            }
            LOG.severe("Error creating work sheet: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(new OpResult(OPERATION_NAME_CREATE, data, tokenID, "Failed to create work sheet."))).build();
        }
    }

    @POST
    @Path("/adjudicate")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateAdjudication(CreateWorkSheetData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        String tokenID = AuthUtil.extractTokenID(authHeader);

        if (tokenID == null) {
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_ADJUDICATION, data, null, "Missing authentication token."))).build();
        }

        Entity requestingUser = AuthUtil.validateToken(datastore, tokenID);
        if (requestingUser == null) {
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_ADJUDICATION, data, tokenID, "Invalid or expired token."))).build();
        }

        String userRole = requestingUser.getString("user_role");
        if (!userRole.equals(ROLE_BACKOFFICE) && !userRole.equals(ROLE_ADMIN)) {
            return Response.status(Status.FORBIDDEN).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_ADJUDICATION, data, tokenID, "Insufficient permissions. Only BACKOFFICE or ADMIN can update adjudication details."))).build();
        }

        if (data == null || data.reference == null || data.reference.isEmpty() || !data.isValidForAdjudication()) {
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_ADJUDICATION, data, tokenID, "Invalid or missing attributes for adjudicating the work sheet."))).build();
        }

        Transaction txn = datastore.newTransaction();
        try {
            Key workSheetKey = datastore.newKeyFactory().setKind(KIND_WORKSHEET).newKey(data.reference);
            Entity workSheet = txn.get(workSheetKey);
            if (workSheet == null) {
                txn.rollback();
                return Response.status(Status.NOT_FOUND).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_ADJUDICATION, data, tokenID, "Work sheet with reference not found."))).build();
            }

            if (ADJUDICATED.equals(workSheet.getString(FIELD_ADJUDICATION_STATUS))) {
                txn.rollback();
                return Response.status(Status.CONFLICT).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_ADJUDICATION, data, tokenID, "Work sheet is already adjudicated."))).build();
            }

            Key partnerKey = datastore.newKeyFactory().setKind(KIND_USER).newKey(data.partnerAccount);
            Entity partner = txn.get(partnerKey);
            if (partner == null || !ROLE_PARTNER.equals(partner.getString("user_role"))) {
                txn.rollback();
                return Response.status(Status.BAD_REQUEST).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_ADJUDICATION, data, tokenID, "Invalid partner account ID."))).build();
            }

            Entity.Builder builder = Entity.newBuilder(workSheet)
                    .set(FIELD_ADJUDICATION_STATUS, ADJUDICATED)
                    .set(FIELD_ADJUDICATION_DATE, data.adjudicationDate)
                    .set(FIELD_START_DATE, data.startDate)
                    .set(FIELD_END_DATE, data.endDate)
                    .set(FIELD_PARTNER_ACCOUNT, data.partnerAccount)
                    .set(FIELD_ADJUDICATING_ENTITY, data.adjudicatingEntity)
                    .set(FIELD_ENTITY_NIF, data.entityNif)
                    .set(FIELD_WORK_STATUS, WORK_STATUS_NOT_STARTED) // Initial work status
                    .set(FIELD_OBSERVATIONS, data.adjudicationObservations);

            txn.put(builder.build());
            txn.commit();

            LOG.info("Work sheet with reference: " + data.reference + " adjudicated to partner: " + data.partnerAccount + " by user: " + requestingUser.getKey().getName());
            return Response.ok(g.toJson(new OpResult(OPERATION_NAME_UPDATE_ADJUDICATION, data, tokenID, "Work sheet adjudicated successfully."))).build();

        } catch (DateTimeParseException e) {
            txn.rollback();
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_ADJUDICATION, data, tokenID, "Invalid date format. Please use YYYY-MM-DD."))).build();
        } catch (Exception e) {
            if (txn.isActive()) {
                txn.rollback();
            }
            LOG.severe("Error updating adjudication details for work sheet: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_ADJUDICATION, data, tokenID, "Failed to update adjudication details."))).build();
        }
    }

    @POST
    @Path("/status")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateWorkStatus(CreateWorkSheetData data, @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        String tokenID = AuthUtil.extractTokenID(authHeader);

        if (tokenID == null) {
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_WORK_STATUS, data, null, "Missing authentication token."))).build();
        }

        Entity requestingUser = AuthUtil.validateToken(datastore, tokenID);
        if (requestingUser == null) {
            return Response.status(Status.UNAUTHORIZED).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_WORK_STATUS, data, tokenID, "Invalid or expired token."))).build();
        }

        String userRole = requestingUser.getString("user_role");
        String username = requestingUser.getKey().getName();

        if (!userRole.equals(ROLE_PARTNER)) {
            return Response.status(Status.FORBIDDEN).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_WORK_STATUS, data, tokenID, "Insufficient permissions. Only PARTNER can update work status."))).build();
        }

        if (data == null || data.reference == null || data.reference.isEmpty() || data.workStatus == null || data.workStatus.isEmpty() ||
                (!data.workStatus.equals(WORK_STATUS_NOT_STARTED) && !data.workStatus.equals(WORK_STATUS_IN_PROGRESS) && !data.workStatus.equals(WORK_STATUS_CONCLUDED))) {
            return Response.status(Status.BAD_REQUEST).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_WORK_STATUS, data, tokenID, "Invalid or missing attributes for updating work status."))).build();
        }

        Transaction txn = datastore.newTransaction();
        try {
            Key workSheetKey = datastore.newKeyFactory().setKind(KIND_WORKSHEET).newKey(data.reference);
            Entity workSheet = txn.get(workSheetKey);
            if (workSheet == null) {
                txn.rollback();
                return Response.status(Status.NOT_FOUND).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_WORK_STATUS, data, tokenID, "Work sheet with reference not found."))).build();
            }

            if (!ADJUDICATED.equals(workSheet.getString(FIELD_ADJUDICATION_STATUS)) ||
                    !username.equals(workSheet.getString(FIELD_PARTNER_ACCOUNT))) {
                txn.rollback();
                return Response.status(Status.FORBIDDEN).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_WORK_STATUS, data, tokenID, "Work sheet is not adjudicated to this partner."))).build();
            }

            Entity.Builder builder = Entity.newBuilder(workSheet)
                    .set(FIELD_WORK_STATUS, data.workStatus)
                    .set(FIELD_OBSERVATIONS, data.statusObservations != null ? data.statusObservations : workSheet.getString(FIELD_OBSERVATIONS)); // Keep existing if not provided

            txn.put(builder.build());
            txn.commit();

            LOG.info("Work sheet with reference: " + data.reference + " status updated to: " + data.workStatus + " by partner: " + username);
            return Response.ok(g.toJson(new OpResult(OPERATION_NAME_UPDATE_WORK_STATUS, data, tokenID, "Work sheet status updated successfully."))).build();

        } catch (Exception e) {
            if (txn.isActive()) {
                txn.rollback();
            }
            LOG.severe("Error updating work status: " + e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(g.toJson(new OpResult(OPERATION_NAME_UPDATE_WORK_STATUS, data, tokenID, "Failed to update work status."))).build();
        }
    }

}