package pt.unl.fct.di.apdc.firstwebapp.util;

import java.util.Set;
import java.util.HashSet;

public class ChangeRoleData {

    public static final String ROLE_SU = "SU";
    public static final String ROLE_GA = "GA";
    public static final String ROLE_GBO = "GBO";
    public static final String ROLE_USER = "USER";
    private static final Set<String> VALID_ROLES = new HashSet<>(Set.of(ROLE_SU, ROLE_GA, ROLE_GBO, ROLE_USER));

    public String userID1; // User initiating the change
    public String userID2; // User whose role is to be changed
    public String newRole; // The target role

    public ChangeRoleData() {
        // Default constructor for JSON deserialization
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

        if (u1RoleUpper.equals(ROLE_SU)) {
            return true;
        }

        if (u1RoleUpper.equals(ROLE_GA)) {
            boolean isUserToGbo = u2RoleUpper.equals(ROLE_USER) && newRoleUpper.equals(ROLE_GBO);
            boolean isGboToUser = u2RoleUpper.equals(ROLE_GBO) && newRoleUpper.equals(ROLE_USER);
            return isUserToGbo || isGboToUser;
        }

        return false;
    }
}