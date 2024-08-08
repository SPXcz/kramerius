package cz.incad.kramerius.auth.thirdparty.keycloack;

import com.google.inject.Inject;
import com.google.inject.Injector;
import cz.incad.kramerius.auth.thirdparty.ThirdPartyUsersSupport;
import cz.incad.kramerius.auth.thirdparty.ExtAuthFilter;
import cz.incad.kramerius.security.UserManager;
import cz.incad.kramerius.users.LoggedUsersSingleton;
import org.keycloak.adapters.spi.KeycloakAccount;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.util.logging.Level;

/**
 * @author happy
 */
public class KeycloakFilter extends ExtAuthFilter {

    @Inject
    UserManager userManager;

//    @Inject
//    LoggedUsersSingleton loggedUsersSingleton;

    private KeycloakUserSupport keycloackUserSupport;

    @Override
    protected ThirdPartyUsersSupport getThirdPartyUsersSupport() {
        return this.keycloackUserSupport;
    }

    @Override
    protected boolean userStoreIsNeeded(HttpServletRequest httpReq) {
        try {
            KeycloakAccount keycloakAccount = (KeycloakAccount) httpReq.getAttribute(KeycloakAccount.class.getName());
            if (keycloakAccount != null && keycloakAccount.getRoles() != null) {
                LOGGER.log(Level.FINE, String.format("Keycloak principal %s (%s)",keycloakAccount.getPrincipal().getName(), keycloakAccount.getRoles().toString()));
            }
            return keycloakAccount != null ;
        }catch (Throwable th){
            LOGGER.log(Level.SEVERE,"Error retrieving KeycloakAccount", th);
        }
        return false;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Injector injector = getInjector(filterConfig);
        injector.injectMembers(this);
        this.keycloackUserSupport = new KeycloakUserSupport();
        this.keycloackUserSupport.setUserManager(this.userManager);
        //this.keycloackUserSupport.setLoggedUsersSingleton(this.loggedUsersSingleton);

    }

    protected Injector getInjector(FilterConfig config) {
        return (Injector) config.getServletContext().getAttribute(Injector.class.getName());
    }

}
