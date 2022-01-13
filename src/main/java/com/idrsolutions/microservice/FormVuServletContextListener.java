package com.idrsolutions.microservice;

import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;
import java.util.Properties;

@WebListener
public class FormVuServletContextListener extends BaseServletContextListener {

    @Override
    public String getConfigPath() {
        String userDir = System.getProperty("user.home");
        if (!userDir.endsWith("/") && !userDir.endsWith("\\")) {
            userDir += System.getProperty("file.separator");
        }
        return userDir + "/.idr/formvu-microservice/";
    }

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        super.contextInitialized(servletContextEvent);
        final Properties propertiesFile = (Properties) servletContextEvent.getServletContext().getAttribute(KEY_PROPERTIES);
        OutputFileServlet.setBasePath(propertiesFile.getProperty("outputPath"));
    }

    @Override
    public String getConfigName(){
        return "formvu-microservice.properties";
    }

}
