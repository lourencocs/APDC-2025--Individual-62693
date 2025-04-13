package pt.unl.fct.di.apdc.firstwebapp.util;

public class UpdateUserData {

    public String authToken;
    public String targetUserID;

    public String profile;
    public String phone;
    public String password;
    public Boolean isPublic;
    public String occupation;
    public String workplace;
    public String address;
    public String postalCode;
    public String NIF;
    public String cc; // New attribute
    public String workplaceNif; // New attribute

    public String role;
    public String state;

    private static final String ROLE_ENDUSER = "ENDUSER";
    private static final String ROLE_PARTNER = "PARTNER";
    private static final String ROLE_BACKOFFICE = "BACKOFFICE";
    private static final String ROLE_ADMIN = "ADMIN";

    public UpdateUserData() {}

    public boolean hasIdentifiers() {
        return authToken != null && !authToken.isEmpty() &&
                targetUserID != null && !targetUserID.isEmpty();
    }

    public boolean hasAttributesToUpdate() {
        return profile != null || phone != null || password != null || isPublic != null ||
                occupation != null || workplace != null || address != null || postalCode != null ||
                NIF != null || role != null || state != null || cc != null || workplaceNif != null;
    }

    public static boolean canTarget(String requesterRole, String targetRole, boolean isSelfModification) {
        String requesterRoleUpper = requesterRole.toUpperCase();
        String targetRoleUpper = targetRole.toUpperCase();

        if (isSelfModification) {
            return requesterRoleUpper.equals(ROLE_ENDUSER) || requesterRoleUpper.equals(ROLE_PARTNER) || requesterRoleUpper.equals(ROLE_BACKOFFICE) || requesterRoleUpper.equals(ROLE_ADMIN);
        } else {
            return switch (requesterRoleUpper) {
                case ROLE_ADMIN -> true; // Admin can target any role
                case ROLE_BACKOFFICE -> targetRoleUpper.equals(ROLE_ENDUSER) || targetRoleUpper.equals(ROLE_PARTNER);
                default -> false;
            };
        }
    }
}