package pt.unl.fct.di.apdc.firstwebapp.util;

// No longer importing incorrect roles

public class ChangeStateData {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_BACKOFFICE = "BACKOFFICE";

    public String userID1;
    public String userID2;
    public String newState;

    public ChangeStateData() {
    }

    public ChangeStateData(String userID1, String userID2, String newState) {
        this.userID1 = userID1;
        this.userID2 = userID2;
        this.newState = newState;
    }

    /**
     * Checks if the provided data is minimally valid (non-empty userIDs).
     * @return true if data is valid, false otherwise.
     */
    public boolean isValid() {
        return userID1 != null && !userID1.trim().isEmpty() &&
                userID2 != null && !userID2.trim().isEmpty() &&
                newState != null && !newState.trim().isEmpty();
    }

    /**
     * Determines if userID1 (userOneRole) is authorized to change the state of userID2
     * to newState based on the defined rules. Role comparisons are case-insensitive.
     * @param userOneRole Current role of the initiating user.
     * @param currentUserState Current state of the target user (userID2).
     * @return true if authorized, false otherwise.
     */
    public boolean authorizeStateChange(String userOneRole, String currentUserState) {
        if (userOneRole == null || currentUserState == null || newState == null) {
            return false;
        }

        String u1RoleUpper = userOneRole.toUpperCase();
        String currentStateUpper = currentUserState.toUpperCase();
        String newStateUpper = newState.toUpperCase();

        if (u1RoleUpper.equals(ROLE_ADMIN)) {
            return true; // Admin can change any state to any state
        }

        if (u1RoleUpper.equals(ROLE_BACKOFFICE)) {
            if ((currentStateUpper.equals("INACTIVE") && newStateUpper.equals("ACTIVE")) ||
                    (currentStateUpper.equals("ACTIVE") && newStateUpper.equals("INACTIVE"))) {
                return true;
            }
        }

        return false;
    }

}