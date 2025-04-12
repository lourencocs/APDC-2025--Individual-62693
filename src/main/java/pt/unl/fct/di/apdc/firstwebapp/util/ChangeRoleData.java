package pt.unl.fct.di.apdc.firstwebapp.util;

import java.util.Set;
import java.util.HashSet;

public class ChangeRoleData {

    // Define valid roles (use constants for better maintenance)
    public static final String ROLE_SU = "SU";
    public static final String ROLE_GA = "GA";
    public static final String ROLE_GBO = "GBO";
    public static final String ROLE_USER = "USER";
    private static final Set<String> VALID_ROLES = new HashSet<>(Set.of(ROLE_SU, ROLE_GA, ROLE_GBO, ROLE_USER));

    public String usernameOne; // User initiating the change
    public String usernameTwo; // User whose role is to be changed
    public String newRole;     // The target role

    public ChangeRoleData() {
        // Default constructor for JSON deserialization
    }

    public ChangeRoleData(String user1, String user2, String newRole) {
        this.usernameOne = user1;
        this.usernameTwo = user2;
        this.newRole = newRole;
    }

    /**
     * Checks if the provided data is minimally valid (non-empty fields and valid newRole).
     * @return true if data is valid, false otherwise.
     */
    public boolean isValid() {
        return usernameOne != null && !usernameOne.trim().isEmpty() &&
                usernameTwo != null && !usernameTwo.trim().isEmpty() &&
                newRole != null && !newRole.trim().isEmpty() &&
                isValidRole(newRole); // Check if newRole is one of the defined valid roles
    }

    /**
     * Checks if a given role string is one of the predefined valid roles (case-insensitive).
     * @param role The role string to check.
     * @return true if the role is valid, false otherwise.
     */
    private boolean isValidRole(String role) {
        return role != null && VALID_ROLES.contains(role.toUpperCase());
    }

    /**
     * Determines if userOne is authorized to change userTwo's role to newRole.
     * Assumes userOneRole and userTwoRole are the *current* roles from the database.
     * Role comparisons are case-insensitive.
     * @param userOneRole Current role of the initiating user.
     * @param userTwoRole Current role of the target user.
     * @return true if authorized, false otherwise.
     */
    public boolean authorizeChange(String userOneRole, String userTwoRole) {
        if (userOneRole == null || userTwoRole == null || !isValidRole(this.newRole)) {
            // Cannot authorize if roles are missing or the target newRole is invalid
            return false;
        }

        String u1RoleUpper = userOneRole.toUpperCase();
        String u2RoleUpper = userTwoRole.toUpperCase();
        String newRoleUpper = this.newRole.toUpperCase(); // Use consistent case for comparison

        if (u1RoleUpper.equals(ROLE_SU)) {
            return true; // SU can do anything
        }

        if (u1RoleUpper.equals(ROLE_GA)) {
            // GA can change USER to GBO or GBO to USER
            boolean isUserToGbo = u2RoleUpper.equals(ROLE_USER) && newRoleUpper.equals(ROLE_GBO);
            boolean isGboToUser = u2RoleUpper.equals(ROLE_GBO) && newRoleUpper.equals(ROLE_USER);
            return isUserToGbo || isGboToUser;
        }

        // GBO and USER cannot change roles
        return false;
    }
}