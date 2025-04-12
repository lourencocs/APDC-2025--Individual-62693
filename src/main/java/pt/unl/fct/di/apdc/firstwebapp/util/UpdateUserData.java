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

    public String role;
    public Boolean state;

    public UpdateUserData() {}

    public boolean hasIdentifiers() {
        return authToken != null && !authToken.isEmpty() &&
                targetUserID != null && !targetUserID.isEmpty();
    }

    public boolean hasAttributesToUpdate() {
        return profile != null || phone != null || password != null || isPublic != null ||
                occupation != null || workplace != null || address != null || postalCode != null ||
                NIF != null || role != null || state != null;
    }

    public static boolean canTarget(String requesterRole, String targetRole, boolean isSelfModification) {
        if (isSelfModification) {
            return requesterRole.equals("USER") || requesterRole.equals("GBO") || requesterRole.equals("GA") || requesterRole.equals("SU");
        } else {
            switch (requesterRole) {
                case "SU":
                    return targetRole.equals("GA") || targetRole.equals("GBO") || targetRole.equals("USER");
                case "GA":
                    return targetRole.equals("GBO") || targetRole.equals("USER");
                case "GBO":
                    return targetRole.equals("USER");
                case "USER":
                    return false;
                default:
                    return false;
            }
        }
    }
}