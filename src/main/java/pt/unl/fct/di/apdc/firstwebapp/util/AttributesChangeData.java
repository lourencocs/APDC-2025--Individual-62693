package pt.unl.fct.di.apdc.firstwebapp.util;


public class AttributesChangeData {

    public String userName, targetID, email, profile, phone, password, role, state, isPublic, occupation,
            workplace, address, postalCode, NIF;

    public AttributesChangeData() {

    }

    public AttributesChangeData(String userName, String targetID, String email, String profile, String phone,
                                String password, String role, String state, String isPublic, String occupation, String workplace, String address,
                                String postalCode, String NIF) {
        this.userName = userName;
        this.targetID = targetID;
        this.email = email;
        this.profile = profile;
        this.phone = phone;
        this.password = password;
        this.role = role;
        this.state = state;
        this.isPublic = isPublic;
        this.occupation = occupation;
        this.workplace = workplace;
        this.address = address;
        this.postalCode = postalCode;
        this.NIF = NIF;
    }

    public boolean validAttributesChange() {
        return userName != null && targetID != null;
    }

    public boolean authorizeAttributesChange(String userRole, String targetRole) {
        if (userRole.equals("SU")) {
            return true;
        } else if (userRole.equals("GA") && (targetRole.equals("GBO") || targetRole.equals("USER"))) {
            return true;
        } else if (userRole.equals("GBO") && targetRole.equals("USER")) {
            return true;
        } else return userRole.equals("USER") && userName.equals(targetID);
    }
}