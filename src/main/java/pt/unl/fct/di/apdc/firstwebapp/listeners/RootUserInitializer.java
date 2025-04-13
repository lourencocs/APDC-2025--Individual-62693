package pt.unl.fct.di.apdc.firstwebapp.listeners;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.*;
import org.apache.commons.codec.digest.DigestUtils;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class RootUserInitializer implements ServletContextListener {

    private final Datastore datastore;

    public RootUserInitializer() {
        DatastoreOptions options = DatastoreOptions.newBuilder()
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
                        .set("user_pwd", DigestUtils.sha512Hex("r00tP@ss"))
                        .set("user_role", "ADMIN")
                        .set("user_state", "active")
                        .set("user_profile", "private")
                        .set("occupation", "NOT DEFINED")
                        .set("workplace", "NOT DEFINED")
                        .set("address", "NOT DEFINED")
                        .set("postal_Code", "NOT DEFINED")
                        .set("NIF", "NOT DEFINED")
                        .set("user_citizen_card", "NOT DEFINED")
                        .set("user_employer_nif", "NOT DEFINED")
                        .set("user_creation_time", Timestamp.now())
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