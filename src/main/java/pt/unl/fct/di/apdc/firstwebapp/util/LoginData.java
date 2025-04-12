package pt.unl.fct.di.apdc.firstwebapp.util;

public class LoginData {

    public String userID; // Changed from username to userID
    public String password;

    public LoginData() {
    }

    public LoginData(String userID, String password) { // Changed username to userID
        this.userID = userID; // Changed username to userID
        this.password = password;
    }
}
