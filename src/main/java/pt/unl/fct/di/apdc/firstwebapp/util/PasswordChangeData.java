package pt.unl.fct.di.apdc.firstwebapp.util;

public class PasswordChangeData {

    public String currentPassword;
    public String newPassword;
    public String newPasswordConfirmation;

    public PasswordChangeData() {}

    public PasswordChangeData( String currentPassword, String newPassword, String newPasswordConfirmation) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.newPasswordConfirmation = newPasswordConfirmation;
    }

    public boolean validPasswordChange() {
        return currentPassword != null && !currentPassword.isEmpty() &&
                newPassword != null && !newPassword.isEmpty() &&
                newPassword.equals(newPasswordConfirmation);
    }

}