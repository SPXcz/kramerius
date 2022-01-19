package cz.incad.Kramerius.views.rights;

import com.google.inject.Inject;
import cz.incad.kramerius.Initializable;
import cz.incad.kramerius.security.licenses.License;
import cz.incad.kramerius.security.licenses.LicensesManager;
import cz.incad.kramerius.security.licenses.LicensesManagerException;

import java.util.logging.Level;
import java.util.logging.Logger;

public class LabelEditView extends AbstractRightsView implements Initializable {

    public static final Logger LOGGER = Logger.getLogger(LabelEditView.class.getName());

    public static final String LABEL_ID_PARAM = "id";

    @Inject
    LicensesManager labelsManager;

    License label;

    @Override
    public void init() {
        try {
            String roleId = this.requestProvider.get().getParameter(LABEL_ID_PARAM);
            if (roleId != null) {
                this.label = this.labelsManager.getLabelById(Integer.parseInt(roleId));
            }
        } catch (LicensesManagerException e) {
            LOGGER.log(Level.SEVERE,e.getMessage(),e);
        }
    }


    public int getId() {
        return this.label != null ? label.getId() : -1;
    }

    public String getName() {
        return label != null ? label.getName() : "";
    }

    public String getDescription() {
        return this.label != null ? label.getDescription() : "";
    }

}
