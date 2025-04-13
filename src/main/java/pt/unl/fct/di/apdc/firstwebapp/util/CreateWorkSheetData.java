package pt.unl.fct.di.apdc.firstwebapp.util;

public class CreateWorkSheetData {

    // Mandatory attributes for creation
    public String reference;
    public String description;
    public String targetType;

    // Attributes for adjudication
    public String adjudicationDate;
    public String startDate;
    public String endDate;
    public String partnerAccount;
    public String adjudicatingEntity;
    public String entityNif;
    public String adjudicationObservations;

    // Attributes for updating work status
    public String workStatus;
    public String statusObservations;

    public CreateWorkSheetData() {
    }

    // Constructor for creating a work sheet
    public CreateWorkSheetData(String reference, String description, String targetType) {
        this.reference = reference;
        this.description = description;
        this.targetType = targetType;
    }

    // Constructor for updating adjudication details
    public CreateWorkSheetData(String reference, String adjudicationDate, String startDate, String endDate,
                               String partnerAccount, String adjudicatingEntity, String entityNif, String adjudicationObservations) {
        this.reference = reference;
        this.adjudicationDate = adjudicationDate;
        this.startDate = startDate;
        this.endDate = endDate;
        this.partnerAccount = partnerAccount;
        this.adjudicatingEntity = adjudicatingEntity;
        this.entityNif = entityNif;
        this.adjudicationObservations = adjudicationObservations;
    }

    // Specific constructor for updating work status
    public CreateWorkSheetData(String reference, String workStatus) {
        this.reference = reference;
        this.workStatus = workStatus;
    }

    public boolean isValidForCreation() {
        return reference != null && !reference.trim().isEmpty() &&
                description != null && !description.trim().isEmpty() &&
                targetType != null && !targetType.trim().isEmpty() &&
                (targetType.equals("Propriedade Pública") || targetType.equals("Propriedade Privada"));
    }

    public boolean isValidForAdjudication() {
        return adjudicationDate != null && !adjudicationDate.trim().isEmpty() &&
                startDate != null && !startDate.trim().isEmpty() &&
                endDate != null && !endDate.trim().isEmpty() &&
                partnerAccount != null && !partnerAccount.trim().isEmpty() &&
                adjudicatingEntity != null && !adjudicatingEntity.trim().isEmpty() &&
                entityNif != null && !entityNif.trim().isEmpty();
    }
}