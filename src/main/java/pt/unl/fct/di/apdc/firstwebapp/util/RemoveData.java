package pt.unl.fct.di.apdc.firstwebapp.util;

import java.util.Objects;

public class RemoveData {

    public String userID1; // User initiating the removal
    public String userID2; // User to be removed

    public RemoveData() {
    }

    public RemoveData(String userID1, String userID2) {
        this.userID1 = userID1;
        this.userID2 = userID2;
    }

    public boolean isValid() {
        return userID1 != null && !userID1.isEmpty() && userID2 != null && !userID2.isEmpty();
    }

    public boolean authorizeChange(String userRole, String targetRole) {
        if (userRole.equals("SU")) {
            return true;
        } else if (userRole.equals("GA") && (targetRole.equals("GBO") || targetRole.equals("USER"))) {
            return true;
        } else {
            return userRole.equals("USER") && Objects.equals(userID1.toUpperCase(), userID2.toUpperCase());
        }
    }
}