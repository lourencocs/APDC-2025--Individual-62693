package pt.unl.fct.di.apdc.firstwebapp.util;

public class PasswordChangeData {

    public String userID, password1, password2, newPassword;


    public PasswordChangeData() {

    }

    public PasswordChangeData(String userID, String password1, String password2, String newPassword) {
        this.userID = userID;
        this.password1 = password1;
        this.password2 = password2;
        this.newPassword = newPassword;
    }

    public boolean validPasswordChange() {
        return userID != null && password1 != null && password1.equals(password2) && newPassword != null;
    }
}