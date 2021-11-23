package com.idrsolutions.microservice;

import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

@WebListener
public class FormVuServletContextListener extends BaseServletContextListener {

    @Override
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        String userDir = System.getProperty("user.home");
        if (!userDir.endsWith("/") && !userDir.endsWith("\\")) {
            userDir += System.getProperty("file.separator");
        }

        final File externalFile = new File(userDir + "/.idr/formvu-microservice/formvu-microservice.properties");

        try {
            if (externalFile.exists()) {
                propertiesFile = new FileInputStream(externalFile.getAbsolutePath());
            } else {
                propertiesFile = servletContextEvent.getServletContext().getResourceAsStream("/WEB_INF/classes/formvu-microservice.properties");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        super.contextInitialized(servletContextEvent);
    }

}
