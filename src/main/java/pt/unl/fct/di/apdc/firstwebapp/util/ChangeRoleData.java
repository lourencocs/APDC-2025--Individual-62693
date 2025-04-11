package pt.unl.fct.di.apdc.firstwebapp.util;

public class ChangeRoleData {

    public String usernameOne, usernameTwo, newRole;



    public ChangeRoleData() {

    }

    public ChangeRoleData(String user1, String user2, String newRole) {
        this.usernameOne = user1;
        this.usernameTwo = user2;
        this.newRole = newRole;
    }

    public boolean authorizeChange(String userOneRole, String userTwoRole) {
        if(userOneRole.toUpperCase().equals("SU")) return true;
        else if(userOneRole.equals("GA")) {
            if(userTwoRole.toUpperCase().equals("GBO") && this.newRole.equals("USER")) {
                return true;
            }
            if(userTwoRole.toUpperCase().equals("USER") && this.newRole.equals("GBO")) {
                return true;
            }
            else return false;
        } else {
            return false;
        }
    }

}