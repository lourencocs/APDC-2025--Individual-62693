package pt.unl.fct.di.apdc.firstwebapp.util;

// Re-use role constants if defined in a shared place, otherwise define/import them
import static pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData.ROLE_SU;
import static pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData.ROLE_GA;
import static pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData.ROLE_GBO;
import static pt.unl.fct.di.apdc.firstwebapp.util.ChangeRoleData.ROLE_USER;

public class ChangeStateData {

    public String usernameOne; // User initiating the change
    public String usernameTwo; // User whose state is to be changed

    public ChangeStateData() {
        // Default constructor for JSON deserialization
    }

    public ChangeStateData(String user1, String user2) {
        this.usernameOne = user1;
        this.usernameTwo = user2;
    }

    /**
     * Checks if the provided data is minimally valid (non-empty usernames).
     * @return true if data is valid, false otherwise.
     */
    public boolean isValid() {
        return usernameOne != null && !usernameOne.trim().isEmpty() &&
                usernameTwo != null && !usernameTwo.trim().isEmpty();
    }

    /**
     * Determines if userOne is authorized to change userTwo's state.
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