package pt.unl.fct.di.apdc.firstwebapp.listeners;

import com.google.cloud.datastore.*;
import org.apache.commons.codec.digest.DigestUtils;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class RootUserInitializer implements ServletContextListener {

    private final Datastore datastore;

    public RootUserInitializer() {
        // Explicitly configure DatastoreOptions for the emulator
        DatastoreOptions options = DatastoreOptions.newBuilder()
                .setHost("localhost:8081") // Or your emulator's host:port
                .setProjectId("projetoadc-456513") // Or your project ID
                .build();
        datastore = options.getService();
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Transaction txn = datastore.newTransaction();
        try {
            Key userKey = datastore.newKeyFactory().setKind("User").newKey("root");
            Entity user = txn.get(userKey);
            if (user == null) {
                user = Entity.newBuilder(userKey)
                        .set("user_name", "root")
                        .set("user_email", "root@example.com")
                        .set("user_phone", "+1234567890")
                        .set("user_pwd", DigestUtils.sha512Hex("rootPassword"))
                        .set("user_role", "SU")
                        .set("user_state", true)
                        .build();
                txn.add(user);
                txn.commit();
            }
        } catch (Exception e) {
            if (txn.isActive()) {
                txn.rollback();
            }
            e.printStackTrace(); // Log the exception
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Nothing to do
    }
}