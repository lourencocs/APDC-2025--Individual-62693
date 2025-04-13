package pt.unl.fct.di.apdc.firstwebapp.util;

public class LoginData {

    public String identifier;
    public String password;

    public LoginData() {
    }

    public LoginData(String identifier, String password) {
        this.identifier = identifier;
        this.password = password;
    }

    public boolean isValid() {
        return identifier != null && !identifier.isEmpty() &&
                password != null && !password.isEmpty();
    }

}
