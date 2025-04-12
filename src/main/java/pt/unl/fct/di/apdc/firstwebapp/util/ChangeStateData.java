package pt.unl.fct.di.apdc.firstwebapp.util;

import static pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData.ROLE_SU;
import static pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData.ROLE_GA;
import static pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData.ROLE_GBO;
import static pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData.ROLE_USER;

public class ChangeStateData {

    public String userID1; // User initiating the change
    public String userID2; // User whose state is to be changed

    public ChangeStateData() {
        // Default constructor for JSON deserialization
    }

    public ChangeStateData(String userID1, String userID2) {
        this.userID1 = userID1;
        this.userID2 = userID2;
    }

    /**
     * Checks if the provided data is minimally valid (non-empty userIDs).
     * @return true if data is valid, false otherwise.
     */
    public boolean isValid() {
        return userID1 != null && !userID1.trim().isEmpty() &&
                userID2 != null && !userID2.trim().isEmpty();
    }

    /**
     * Determines if userID1 is authorized to change userID2's state.
     * Assumes userOneRole and userTwoRole are the *current* roles from the database.
     * Role comparisons are case-insensitive.
     * @param userOneRole Current role of the initiating user.
     * @param userTwoRole Current role of the target user.
     * @return true if authorized, false otherwise.
     */
    public boolean authorizeStateChange(String userOneRole, String userTwoRole) {
        if (userOneRole == null || userTwoRole == null) {
            return false; // Cannot authorize if roles are missing
        }

        String u1RoleUpper = userOneRole.toUpperCase();
        String u2RoleUpper = userTwoRole.toUpperCase();

        // SU can change anyone's state
        if (u1RoleUpper.equals(ROLE_SU)) {
            return true;
        }

        // GA can change USER or GBO state
        if (u1RoleUpper.equals(ROLE_GA)) {
            return u2RoleUpper.equals(ROLE_USER) || u2RoleUpper.equals(ROLE_GBO);
        }

        // GBO can change USER state
        if (u1RoleUpper.equals(ROLE_GBO)) {
            return u2RoleUpper.equals(ROLE_USER);
        }

        // USER cannot change anyone's state (implicitly handled by falling through)
        return false;
    }
}