package pt.unl.fct.di.apdc.firstwebapp.util;

public class RegisterData {

    public String userID;
    public String username;
    public String name;
    public String password;
    public String passwordConfirmation;
    public String email;
    public String phone;

    public String profile;
    public String occupation, workplace, address, postalCode, NIF, citizenCardNumber, employerNIF;

    public RegisterData() {
    }

    public RegisterData(String userID, String name, String email, String phone, String password, String passwordConfirmation,
                        String profile, String occupation, String workplace, String address, String postalCode,
                        String NIF, String citizenCardNumber, String employerNIF) {
        this.userID = userID;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.passwordConfirmation = passwordConfirmation;
        this.profile = profile;
        this.occupation = occupation;
        this.workplace = workplace;
        this.address = address;
        this.postalCode = postalCode;
        this.NIF = NIF;
        this.citizenCardNumber = citizenCardNumber;
        this.employerNIF = employerNIF;
    }

    public boolean validRegistration() {
        return userID != null && !userID.isEmpty()
                && name != null && !name.isEmpty()
                && password != null && !password.isEmpty()
                && email != null && !email.isEmpty()
                && phone != null && !phone.isEmpty()
                && passwordConfirmation != null && !passwordConfirmation.isEmpty()
                && profile != null && (profile.equals("public") || profile.equals("private"));
    }
}