package pt.unl.fct.di.apdc.firstwebapp.util;

import java.util.Set;
import java.util.HashSet;

public class ChangeRoleData {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_BACKOFFICE = "BACKOFFICE";
    public static final String ROLE_PARTNER = "PARTNER";
    public static final String ROLE_ENDUSER = "ENDUSER";

    private static final Set<String> VALID_ROLES = new HashSet<>(Set.of(ROLE_ADMIN, ROLE_BACKOFFICE, ROLE_PARTNER, ROLE_ENDUSER));

    public String userID1;
    public String userID2;
    public String newRole;

    public ChangeRoleData() {
    }

    public ChangeRoleData(String userID1, String userID2, String newRole) {
        this.userID1 = userID1;
        this.userID2 = userID2;
        this.newRole = newRole;
    }

    public boolean isValid() {
        return userID1 != null && !userID1.trim().isEmpty() &&
                userID2 != null && !userID2.trim().isEmpty() &&
                newRole != null && !newRole.trim().isEmpty() &&
                isValidRole(newRole);
    }

    private boolean isValidRole(String role) {
        return role != null && VALID_ROLES.contains(role.toUpperCase());
    }

    public boolean authorizeChange(String userOneRole, String userTwoRole) {
        if (userOneRole == null || userTwoRole == null || !isValidRole(this.newRole)) {
            return false;
        }

        String u1RoleUpper = userOneRole.toUpperCase();
        String u2RoleUpper = userTwoRole.toUpperCase();
        String newRoleUpper = this.newRole.toUpperCase();

        if (u1RoleUpper.equals(ROLE_ADMIN)) {
            return true;
        }

        if (u1RoleUpper.equals(ROLE_BACKOFFICE)) {
            boolean isEndUserToPartner = u2RoleUpper.equals(ROLE_ENDUSER) && newRoleUpper.equals(ROLE_PARTNER);
            boolean isPartnerToEndUser = u2RoleUpper.equals(ROLE_PARTNER) && newRoleUpper.equals(ROLE_ENDUSER);
            return isEndUserToPartner || isPartnerToEndUser;
        }

        return false;
    }
}