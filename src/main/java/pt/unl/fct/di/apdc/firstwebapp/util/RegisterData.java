package pt.unl.fct.di.apdc.firstwebapp.util;

public class RegisterData {

    public String username;
    public String password;
    public String email;
    public String profile;
    public int phone;

    public String isPublic, occupation, workplace, address, postalCode, NIF;

    public byte[] profilePic;

    public RegisterData() {

    }

    public RegisterData(String username,String email, String profile, int phone, String password, String isPublic,
                        String occupation, String workplace, String address, String postalCode,String NIF,byte[] profilePic) {
        this.username = username;
        this.email = email;
        this.profile = profile;
        this.phone = phone;
        this.password = password;
        this.isPublic = isPublic;
        this.occupation = occupation;
        this.workplace = workplace;
        this.address = address;
        this.postalCode = postalCode;
        this.NIF = NIF;
        this.profilePic = profilePic;
    }


    public boolean validRegistration() {
        if(username == null || password == null || email == null || profile == null ) {
            return false;
        }
        return true;
    }
}
