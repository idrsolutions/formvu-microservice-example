/*
 * FormVu Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/formvu-microservice-example
 *
 * Copyright 2023 IDRsolutions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.idrsolutions.microservice;

import com.idrsolutions.microservice.db.DBHandler;

import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
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
    public void contextInitialized(final ServletContextEvent servletContextEvent) {
        super.contextInitialized(servletContextEvent);
        final Properties propertiesFile = (Properties) servletContextEvent.getServletContext().getAttribute(KEY_PROPERTIES);
        OutputFileServlet.setBasePath(propertiesFile.getProperty(KEY_PROPERTY_OUTPUT_PATH));

        if (DBHandler.isUsingMemoryDatabase()) {
            final String message = "It is recommended to set your own database instead of using the default internal database as it will allow you to more easily scale the service in the future.\n" +
                    "More details on the benefits and how to do this can be found here https://support.idrsolutions.com/formvu/tutorials/cloud/options/external-state-database";
            LOG.log(Level.WARNING, message);
        }

        try {
            final String remoteTrackingPort = propertiesFile.getProperty(KEY_PROPERTY_REMOTE_TRACKING_PORT);
            final Registry reg = createRegistry(remoteTrackingPort);
            propertiesFile.put(KEY_PROPERTY_REMOTE_TRACKING_REGISTRY, reg);
        } catch (final RemoteException e) {
            LOG.log(Level.WARNING, "Unable to create Registry to allow conversion tracking.");
        }
    }

    private Registry createRegistry(final String remoteTrackingPort) throws RemoteException {
        try {
            return LocateRegistry.createRegistry(Integer.parseInt(remoteTrackingPort));
        } catch (final ExportException ex) {
            return LocateRegistry.getRegistry(Integer.parseInt(remoteTrackingPort));
        }
    }

    @Override
    public void contextDestroyed(final ServletContextEvent servletContextEvent) {
        super.contextDestroyed(servletContextEvent);

        final Properties propertiesFile = (Properties) servletContextEvent.getServletContext().getAttribute(KEY_PROPERTIES);
        final Registry registry = ((Registry) propertiesFile.get(KEY_PROPERTY_REMOTE_TRACKING_REGISTRY));
        if (registry != null) {
            try {
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (final NoSuchObjectException e) {
                LOG.log(Level.SEVERE, "Unable to stop Registry for conversion tracking.");
            }
        }
    }

    @Override
    protected void validateConfigFileValues(final Properties propertiesFile) {
        super.validateConfigFileValues(propertiesFile);

        validateRemoteTrackerPort(propertiesFile);
    }


    private static void validateRemoteTrackerPort(final Properties properties) {
        final String remoteTrackingPort = properties.getProperty(KEY_PROPERTY_REMOTE_TRACKING_PORT);
        if (remoteTrackingPort == null || remoteTrackingPort.isEmpty() || !remoteTrackingPort.matches("\\d+")) {
            properties.setProperty(KEY_PROPERTY_REMOTE_TRACKING_PORT, "1099");
            LOG.log(Level.WARNING, "Properties value for \"remoteTracker.port\" was not set. Using a value of \"1099\"");
        }
    }

    @Override
    public String getConfigName(){
        return "formvu-microservice.properties";
    }

}
