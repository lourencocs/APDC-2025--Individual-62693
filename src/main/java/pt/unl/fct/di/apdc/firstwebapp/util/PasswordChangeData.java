package pt.unl.fct.di.apdc.firstwebapp.util;

public class PasswordChangeData {

    // Changed from userID to authToken
    public String authToken;
    // Keep password fields
    public String currentPassword; // Renamed from password1 for clarity
    public String newPassword;
    public String newPasswordConfirmation; // Renamed from password2

    public PasswordChangeData() {}

    // Constructor updated
    public PasswordChangeData(String authToken, String currentPassword, String newPassword, String newPasswordConfirmation) {
        this.authToken = authToken;
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.newPasswordConfirmation = newPasswordConfirmation;
    }

    // Validation updated
    public boolean validPasswordChange() {
        // Check token presence, password presence, and if new passwords match
        return authToken != null && !authToken.isEmpty() &&
                currentPassword != null && !currentPassword.isEmpty() &&
                newPassword != null && !newPassword.isEmpty() &&
                newPassword.equals(newPasswordConfirmation);
        // Add complexity check for newPassword if desired
        // && isValidPasswordComplexity(newPassword);
    }

}