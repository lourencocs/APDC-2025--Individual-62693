package pt.unl.fct.di.apdc.firstwebapp.util;

public class RegisterData {

    public String username;
    public String password;
    public String email;
    public String profile;
    public String phone;
    public boolean isPublic;

    public String occupation, workplace, address, postalCode, NIF;

    public byte[] profilePic;

    public RegisterData() {
    }

    public RegisterData(String username, String email, String profile, String phone, String password, boolean isPublic,
                        String occupation, String workplace, String address, String postalCode, String NIF, byte[] profilePic) {
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
        return username != null && !username.isEmpty()
                && password != null && !password.isEmpty()
                && email != null && !email.isEmpty()
                && phone != null && !phone.isEmpty();
    }
}