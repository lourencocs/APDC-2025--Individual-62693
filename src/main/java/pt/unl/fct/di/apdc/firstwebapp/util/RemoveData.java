package pt.unl.fct.di.apdc.firstwebapp.util;

import java.util.Objects;

public class RemoveData {

    public String userID;
    public String targetID;

    public RemoveData() {
    }

    public RemoveData(String userID, String targetID) {
        this.userID = userID;
        this.targetID = targetID;
    }

    public boolean validChange() {
        return userID != null && !userID.isEmpty() && targetID != null && !targetID.isEmpty();
    }

    public boolean authorizeChange(String userRole, String targetRole) {
        if (userRole.equals("SU")) {
            return true;
        } else if (userRole.equals("GA") && (targetRole.equals("GBO") || targetRole.equals("USER"))) {
            return true;
        } else {
            return userRole.equals("USER") && Objects.equals(userID.toUpperCase(), targetID.toUpperCase());
        }
    }
}