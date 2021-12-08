package com.idrsolutions.microservice;

import javax.servlet.annotation.WebListener;
import java.util.Properties;
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

        super.validateConfigFileValues(propertiesFile);
        
    }

}
