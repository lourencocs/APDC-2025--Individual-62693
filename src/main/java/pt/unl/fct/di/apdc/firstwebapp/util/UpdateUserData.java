package pt.unl.fct.di.apdc.firstwebapp.util;

public class UpdateUserData {

    // Authentication & Targeting
    public String authToken; // Token of the user making the request
    public String targetUsername; // Username of the user whose attributes are to be changed

    // Modifiable Attributes (nullable - only set if change is desired)
    public String profile;
    public String phone;
    public String password; // New password (will be hashed)
    public Boolean isPublic; // Use Boolean object type for nullability
    public String occupation;
    public String workplace;
    public String address;
    public String postalCode;
    public String NIF;

    // Attributes modifiable only by privileged users (SU/GA/GBO on valid targets)
    public String role; // New role
    public Boolean state; // New state (true=ACTIVE, false=INACTIVE)

    // Attributes that should NOT be directly modifiable via this operation
    // email, name (username is targetUsername)

    public UpdateUserData() {
        // Default constructor for JSON deserialization
    }

    /**
     * Basic validation: Checks if authentication token and target user ID are provided.
     * More specific attribute validation can be added here or in the resource if needed.
     * @return true if basic identifiers are present, false otherwise.
     */
    public boolean hasIdentifiers() {
        return authToken != null && !authToken.isEmpty() &&
                targetUsername != null && !targetUsername.isEmpty();
    }

    /**
     * Checks if there is at least one attribute provided for update.
     * @return true if at least one modifiable attribute is not null.
     */
    public boolean hasAttributesToUpdate() {
        return profile != null || phone != null || password != null || isPublic != null ||
                occupation != null || workplace != null || address != null || postalCode != null ||
                NIF != null || role != null || state != null;
    }

    /**
     * Static method to check if a requester role can target a target role based on OP6.
     * This is separated for clarity and potential reuse.
     * @param requesterRole Role of the user making the request.
     * @param targetRole Role of the user being modified.
     * @param isSelfModification True if the requester is modifying their own account.
     * @return true if the operation is permitted by role hierarchy, false otherwise.
     */
    public static boolean canTarget(String requesterRole, String targetRole, boolean isSelfModification) {
        if (isSelfModification) {
            // USER can modify self, others can too (but specific attributes are restricted later)
            return requesterRole.equals("USER") || requesterRole.equals("GBO") || requesterRole.equals("GA") || requesterRole.equals("SU");
        } else {
            // Rules for modifying OTHERS
            switch (requesterRole) {
                case "SU":
                    // SU can modify GA, GBO, USER (cannot modify other SU based on OP6 text)
                    return targetRole.equals("GA") || targetRole.equals("GBO") || targetRole.equals("USER");
                case "GA":
                    // GA can modify GBO, USER
                    return targetRole.equals("GBO") || targetRole.equals("USER");
                case "GBO":
                    // GBO can modify USER
                    return targetRole.equals("USER");
                case "USER":
                    // USER cannot modify others
                    return false;
                default:
                    return false; // Unknown role cannot target anyone
            }
        }
    }
}