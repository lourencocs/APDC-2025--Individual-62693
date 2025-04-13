package pt.unl.fct.di.apdc.firstwebapp.util;

import java.util.Objects;

public class RemoveData {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_BACKOFFICE = "BACKOFFICE";
    public static final String ROLE_ENDUSER = "ENDUSER";
    public static final String ROLE_PARTNER = "PARTNER";

    public String userID1; // User initiating the removal
    public String userID2;
    public String email;// User to be removed

    public RemoveData() {
    }

    public RemoveData(String userID1, String userID2, String email) {
        this.userID1 = userID1;
        this.userID2 = userID2;
        this.email = email;
    }

    public boolean isValid() {
        return userID1 != null && !userID1.trim().isEmpty() &&
                ( (userID2 != null && !userID2.trim().isEmpty()) || (email != null && !email.trim().isEmpty()) );
    }

    public boolean authorizeChange(String userRole, String targetRole) {
        if (userRole == null || targetRole == null) {
            return false;
        }

        String userRoleUpper = userRole.toUpperCase();
        String targetRoleUpper = targetRole.toUpperCase();

        if (userRoleUpper.equals(ROLE_ADMIN)) {
            return true;
        }

        if (userRoleUpper.equals(ROLE_BACKOFFICE)) {
            return targetRoleUpper.equals(ROLE_ENDUSER) || targetRoleUpper.equals(ROLE_PARTNER);
        }

        return false;
    }
}