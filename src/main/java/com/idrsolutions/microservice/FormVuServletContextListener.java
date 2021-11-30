package com.idrsolutions.microservice;

import javax.servlet.annotation.WebListener;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class FormVuServletContextListener extends BaseServletContextListener {

    private static final Logger LOG = Logger.getLogger(FormVuServletContextListener.class.getName());

    @Override
    public String getConfigPath() {
        String userDir = System.getProperty("user.home");
        if (!userDir.endsWith("/") && !userDir.endsWith("\\")) {
            userDir += System.getProperty("file.separator");
        }
        return userDir + "/.idr/formvu-microservice/";
    }

    @Override
    public String getConfigName(){
        return "formvu-microservice.properties";
    }

    @Override
    public void validateConfigFileValues(final Properties propertiesFile) {

        //service.concurrentConversion
        validateConcurrentConversions(propertiesFile);

    }

    private static void validateConcurrentConversions(final Properties properties) {
        String concurrentConversions = properties.getProperty("service.concurrentConversion");
        if (concurrentConversions == null || concurrentConversions.isEmpty() || !concurrentConversions.matches("\\d+") || Integer.parseInt(concurrentConversions) <= 0) {
            final int availableProcessors = Runtime.getRuntime().availableProcessors();
            properties.setProperty("service.concurrentConversion", "" + availableProcessors);
            final String logDefaultUse = "Properties value for \"service.concurrentConversion\" incorrect, should be a positive integer. Using a value of " + availableProcessors + " based on available processors";
            LOG.log(Level.WARNING, logDefaultUse);
        }

    }
}
