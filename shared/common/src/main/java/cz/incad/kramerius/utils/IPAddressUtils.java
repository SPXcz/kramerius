package cz.incad.kramerius.utils;

import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import cz.incad.kramerius.utils.conf.KConfiguration;
import org.apache.commons.configuration.Configuration;
import cz.incad.kramerius.utils.conf.KConfiguration;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IPAddressUtils {

    private static final String X_IP_FORWARED_ENABLED_FOR_KEY = "x_ip_forwarded_enabled_for";

    public static Logger LOGGER = Logger.getLogger(IPAddressUtils.class.getName());

    public static final String X_IP_FORWARD = "x-forwarded-for";
    public static String[] LOCALHOSTS = {"127.0.0.1", "localhost", "0:0:0:0:0:0:0:1", "::1"};

    static {
        try {
            IPAddressUtils.LOCALHOSTS = NetworkUtils.getLocalhostsAddress();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            IPAddressUtils.LOCALHOSTS = new String[]{"127.0.0.1", "localhost", "0:0:0:0:0:0:0:1", "::1"};
        }
    }

    public static String getRemoteAddress(HttpServletRequest httpReq)  {
		return getRemoteAddress(httpReq, KConfiguration.getInstance().getConfiguration());
	}

    public static String getRemoteAddress(HttpServletRequest httpReq, Configuration conf) {
        //String headerFowraded = httpReq.getHeader(X_IP_FORWARD);

        String headerFowraded = null;
        Enumeration headerNames = httpReq.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String header = headerNames.nextElement().toString();
            if(header.toLowerCase().equals(X_IP_FORWARD)) {
                headerFowraded = httpReq.getHeader(header);
            }
        }

        if (StringUtils.isAnyString(headerFowraded) && IPAddressUtils.matchConfigurationAddress(httpReq,conf)) {
            if (headerFowraded.contains(",")) {
                return headerFowraded.split(",")[0];
            } else return headerFowraded;
        } else {
            return httpReq.getRemoteAddr();
        }
    }

    public static boolean matchConfigurationAddress(HttpServletRequest httpReq, Configuration conf) {
        String remoteAddr = httpReq.getRemoteAddr();
        List<String> forwaredEnabled = Lists.transform(conf.getList(X_IP_FORWARED_ENABLED_FOR_KEY, Arrays.asList(LOCALHOSTS)), Functions.toStringFunction());
        if (!forwaredEnabled.isEmpty()) {
            for (String pattern : forwaredEnabled) {
                if (remoteAddr.matches(pattern)) return true;
            }
        }
        return false;
    }


    public static boolean matchConfigurationAddress(HttpServletRequest httpReq) {
		return matchConfigurationAddress(httpReq,  KConfiguration.getInstance().getConfiguration());
    }

}
