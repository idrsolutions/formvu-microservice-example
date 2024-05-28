/*
 * FormVu Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/formvu-microservice-example
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
import com.idrsolutions.microservice.storage.Storage;
import com.idrsolutions.microservice.utils.DefaultFileServlet;
import com.idrsolutions.microservice.utils.ProcessUtils;
import com.idrsolutions.microservice.utils.ZipHelper;
import org.jpedal.PdfDecoderServer;
import org.jpedal.exception.PdfException;
import org.jpedal.settings.FormVuSettingsValidator;

import javax.json.stream.JsonParsingException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.io.File.separator;

/**
 * Provides an API to use FormVu on its own dedicated app server. See the API
 * documentation for more information on how to interact with this servlet.
 *
 * @see BaseServlet
 */
@WebServlet(name = "formvu", urlPatterns = "/formvu", loadOnStartup = 1)
@MultipartConfig
public class FormVuServlet extends BaseServlet {

    private static final Logger LOG = Logger.getLogger(FormVuServlet.class.getName());

    static {
        setInputPath(USER_HOME + "/.idr/formvu-microservice/input/");
        setOutputPath(USER_HOME + "/.idr/formvu-microservice/output/");
        OutputFileServlet.setBasePath(USER_HOME + "/.idr/formvu-microservice/output");
    }

    /**
     * Converts given pdf file to html using FormVu.
     * <p>
     * See API docs for information on how this method communicates via the
     * individual object to the client.
     *
     * @param uuid The uuid of the conversion
     * @param inputFile The input file
     * @param outputDir The output directory of the converted file
     * @param contextUrl The context that this servlet is running in
     */
    @Override
    protected void convert(final String uuid, final File inputFile,
                           final File outputDir, final String contextUrl) {
        final Map<String, String> conversionParams;
        try {
            final Map<String, String> settings = DBHandler.getInstance().getSettings(uuid);
            conversionParams = settings != null ? settings : new HashMap<>();
        } catch (final SQLException e) {
            DBHandler.getInstance().setError(uuid, 500, "Database failure");
            return;
        }

        final String fileName = inputFile.getName();
        final String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
        final String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));

        if (fileNameWithoutExt.isEmpty() || ".".equals(fileNameWithoutExt) || "..".equals(fileNameWithoutExt)) {
            DBHandler.getInstance().setError(uuid, 1090, "Disallowed filename");
            return;
        }

        // To avoid repeated calls to getAbsolutePath()
        final String outputDirStr = outputDir.getAbsolutePath();
        final Properties properties =
                (Properties) getServletContext().getAttribute(BaseServletContextListener.KEY_PROPERTIES);

        if (!"pdf".equalsIgnoreCase(ext)) {
            DBHandler.getInstance().setError(uuid, 1070,
                    "Internal error processing file - input file must be a PDF Form File");
            return;
        }

        //Makes the directory for the output file
        new File(outputDirStr + separator + fileNameWithoutExt).mkdirs();
        try {
            final PdfDecoderServer decoder = new PdfDecoderServer(false);
            decoder.openPdfFile(inputFile.getAbsolutePath());
            decoder.setEncryptionPassword(conversionParams.getOrDefault("org.jpedal.pdf2html.password", ""));

            final boolean isForm = decoder.isForm();
            final boolean incorrectPassword = decoder.isEncrypted() && !decoder.isPasswordSupplied();
            final int pageCount = decoder.getPageCount();

            decoder.closePdfFile();
            decoder.dispose();

            if (incorrectPassword) {
                LOG.log(Level.SEVERE, "Invalid Password");
                DBHandler.getInstance().setError(uuid, 1070, "Invalid password supplied.");
                return;
            }

            if (!isForm) {
                LOG.log(Level.SEVERE, "Invalid PDF - Provided PDF file does not contain any forms.");
                DBHandler.getInstance().setError(uuid, 1060,
                        "Invalid PDF - Provided PDF file does not contain any forms.");
                return;
            }

            DBHandler.getInstance().setCustomValue(uuid, "pageCount", String.valueOf(pageCount));
            DBHandler.getInstance().setCustomValue(uuid, "pagesConverted", "0");

        } catch (final PdfException e) {
            LOG.log(Level.SEVERE, "Invalid PDF", e);
            DBHandler.getInstance().setError(uuid, 1060, "Invalid PDF");
            return;
        }

        try {
            DBHandler.getInstance().setState(uuid, "processing");

            final String servletDirectory = getServletContext().getRealPath("");
            final String webappDirectory;
            if (servletDirectory != null) {
                webappDirectory = servletDirectory + "/WEB-INF/lib/formvu.jar";
            } else {
                webappDirectory = "WEB-INF/lib/formvu.jar";
            }

            final long maxDuration =
                    Long.parseLong(properties.getProperty(BaseServletContextListener.KEY_PROPERTY_MAX_CONVERSION_DURATION));
            final ProcessUtils.Result result = convertFileToHTML(conversionParams, uuid,
                    webappDirectory, inputFile, outputDir, maxDuration);

            switch (result) {
                case SUCCESS:
                    ZipHelper.zipFolder(outputDirStr + separator + fileNameWithoutExt,
                            outputDirStr + separator + fileNameWithoutExt + ".zip");
                    final String outputPathInDocroot = uuid + '/' + DefaultFileServlet.encodeURI(fileNameWithoutExt);
                    DBHandler.getInstance().setCustomValue(uuid, "previewUrl",
                            contextUrl + "/output/" + outputPathInDocroot + "/form.html");
                    DBHandler.getInstance().setCustomValue(uuid, "downloadUrl",
                            contextUrl + "/output/" + outputPathInDocroot + ".zip");

                    final Storage storage = (Storage) getServletContext().getAttribute("storage");
                    if (storage != null) {
                        final String remoteUrl =
                                storage.put(new File(outputDirStr + separator + fileNameWithoutExt + ".zip"),
                                fileNameWithoutExt + ".zip", uuid);
                        DBHandler.getInstance().setCustomValue(uuid, "remoteUrl", remoteUrl);
                    }
                    DBHandler.getInstance().setState(uuid, "processed");
                    break;
                case TIMEOUT:
                    final String message = String.format("Conversion %s exceeded max duration of %dms", uuid,
                            maxDuration);
                    LOG.log(Level.INFO, message);
                    DBHandler.getInstance().setError(uuid, 1230,
                            "Conversion exceeded max duration of " + maxDuration + "ms");
                    break;
                case ERROR:
                    LOG.log(Level.SEVERE, "An error occurred during the conversion");
                    DBHandler.getInstance().setError(uuid, 1220,
                            "An error occurred during the conversion");
                    break;
            }
        } catch (final Throwable ex) {
            LOG.log(Level.SEVERE, "Exception thrown when converting input", ex);
            DBHandler.getInstance().setError(uuid, 1220,
                    "Exception thrown when converting input: " + ex.getMessage());
        }
    }

    private ProcessUtils.Result convertFileToHTML(final Map<String, String> conversionParams, final String uuid, final String webappDirectory, final File inputPdf, final File outputDir, final long maxDuration) {
        final ArrayList<String> commandArgs = new ArrayList<>();
        commandArgs.add("java");

        final Properties properties =
                (Properties) getServletContext().getAttribute(BaseServletContextListener.KEY_PROPERTIES);
        final int memoryLimit =
                Integer.parseInt(properties.getProperty(BaseServletContextListener.KEY_PROPERTY_CONVERSION_MEMORY));
        final int remoteTrackerPort = Integer.parseInt((String) properties.get(BaseServletContextListener.KEY_PROPERTY_REMOTE_TRACKING_PORT));

        if (memoryLimit > 0) {
            commandArgs.add("-Xmx" + memoryLimit + 'M');
        }

        if (!conversionParams.isEmpty()) {
            final Set<String> keys = conversionParams.keySet();
            for (final String key : keys) {
                final String value = conversionParams.get(key);
                commandArgs.add("-D" + key + '=' + value);
            }
        }

        commandArgs.add("-Dcom.idrsolutions.remoteTracker.port=" + remoteTrackerPort);
        commandArgs.add("-Dcom.idrsolutions.remoteTracker.uuid=" + uuid);

        commandArgs.add("-cp");
        commandArgs.add(webappDirectory);
        commandArgs.add("org.jpedal.examples.html.FormVu");
        commandArgs.add(inputPdf.getAbsolutePath());
        commandArgs.add(outputDir.getAbsolutePath());

        return ProcessUtils.runProcess(commandArgs.toArray(new String[0]), inputPdf.getParentFile(), uuid,
                "FormVu Conversion", maxDuration);
    }

    /**
     * Validates the settings parameter passed to the request. It will parse the conversionParams,
     * validate them, and then set the params in the Individual object.
     * <p>
     * If settings are not parsed or validated, doError will be called.
     *
     * @param request the request for this conversion
     * @param response the response object for the request
     * @param uuid the uuid of this conversion
     * @return true if the settings are parsed and validated successfully, false if not
     */
    @Override
    protected boolean validateRequest(final HttpServletRequest request, final HttpServletResponse response,
                                      final String uuid) {

        final Map<String, String> settings;
        try {
            settings = parseSettings(request.getParameter("settings"));
        } catch (final JsonParsingException exception) {
            doError(request, response,
                    "Error encountered when parsing settings JSON <" + exception.getMessage() + '>', 400);
            return false;
        }

        try {
            FormVuSettingsValidator.validate(settings, false);
        } catch (final IllegalArgumentException e) {
            doError(request, response, e.getMessage(), 400);
            return false;
        }

        request.setAttribute("com.idrsolutions.microservice.settings", settings);

        return true;
    }
}
