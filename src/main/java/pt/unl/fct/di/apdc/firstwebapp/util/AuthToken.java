package pt.unl.fct.di.apdc.firstwebapp.util;

import java.util.UUID;

public class AuthToken {

    // Keep expiration time configurable maybe, but 2 hours is fine for example
    public static final long EXPIRATION_TIME = 1000 * 60 * 60 * 2; // 2 hours in milliseconds

    public String username;
    public String tokenID; // The unique ID for this token instance (UUID)
    public String role; // Role of the user at the time of token creation
    public long creationData; // Use standard Java naming convention (creationDate)
    public long expirationData; // Use standard Java naming convention (expirationDate)
    public String verifier; // The "magic number" / proof of authenticity

    // Constructor for creating a new token upon successful login
    public AuthToken(String username, String role) {
        this.username = username;
        this.role = role;
        this.tokenID = UUID.randomUUID().toString();
        this.verifier = UUID.randomUUID().toString(); // Use another UUID as a simple verifier
        this.creationData = System.currentTimeMillis();
        this.expirationData = this.creationData + EXPIRATION_TIME;
    }

    // Default constructor for frameworks that might need it (like some JSON libraries)
    public AuthToken() {}

    // Constructor to potentially reconstruct from Datastore - might not be needed if using direct Entity mapping
    public AuthToken(String username, String tokenID, String role, long creationData, long expirationData, String verifier) {
        this.username = username;
        this.tokenID = tokenID;
        this.role = role;
        this.creationData = creationData;
        this.expirationData = expirationData;
        this.verifier = verifier;
    }
}
