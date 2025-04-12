package pt.unl.fct.di.apdc.firstwebapp.util;

public class PasswordChangeData {

    public String authToken;
    public String currentPassword;
    public String newPassword;
    public String newPasswordConfirmation;

    public PasswordChangeData() {}


    public PasswordChangeData(String authToken, String currentPassword, String newPassword, String newPasswordConfirmation) {
        this.authToken = authToken;
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.newPasswordConfirmation = newPasswordConfirmation;
    }

    // Validation updated
    public boolean validPasswordChange() {
        return authToken != null && !authToken.isEmpty() &&
                currentPassword != null && !currentPassword.isEmpty() &&
                newPassword != null && !newPassword.isEmpty() &&
                newPassword.equals(newPasswordConfirmation);
    }

}