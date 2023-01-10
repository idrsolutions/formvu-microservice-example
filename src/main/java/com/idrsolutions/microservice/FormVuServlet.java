/*
 * FormVu Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/formvu-microservice-example
 *
 * Copyright 2022 IDRsolutions
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
import com.idrsolutions.microservice.utils.ConversionTracker;
import com.idrsolutions.microservice.utils.DefaultFileServlet;
import com.idrsolutions.microservice.utils.ZipHelper;
import org.jpedal.PdfDecoderServer;
import org.jpedal.examples.html.PDFtoHTML5Converter;
import org.jpedal.exception.PdfException;
import org.jpedal.render.output.FormViewerOptions;
import org.jpedal.render.output.html.HTMLConversionOptions;
import org.jpedal.settings.FormVuSettingsValidator;

import javax.json.stream.JsonParsingException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final String[] validTextModeOptions = {
            "svg_realtext",
            "svg_shapetext_selectable",
            "svg_shapetext_nonselectable",
            "image_realtext",
            "image_shapetext_selectable",
            "image_shapetext_nonselectable"};

    private static final String[] validOutputContentModes = {
            "DEFAULT",
            "NO_FDFXFADUMP",
            "REDUCED_CONTENT",
            "NO_MENU"};

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
    protected void convert(String uuid,
                           File inputFile, File outputDir, String contextUrl) {

        final Map<String, String> conversionParams;
        try {
            final Map<String, String> settings = DBHandler.getInstance().getSettings(uuid);
            conversionParams = settings != null ? settings : new HashMap<>();
        } catch (final SQLException e) {
            DBHandler.getInstance().setError(uuid, 500, "Database failure");
            return;
        }

        final String fileName = inputFile.getName();
        final String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
        final String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        // To avoid repeated calls to getParent() and getAbsolutePath()
        final String inputDir = inputFile.getParent();
        final String outputDirStr = outputDir.getAbsolutePath();
        final Properties properties = (Properties) getServletContext().getAttribute(BaseServletContextListener.KEY_PROPERTIES);

        if (!"pdf".equalsIgnoreCase(ext)) {
            DBHandler.getInstance().setError(uuid, 1070, "Internal error processing file - input file must be a PDF Form File");
            return;
        }

        //Makes the directory for the output file
        new File(outputDirStr + "/" + fileNameWithoutExt).mkdirs();
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
                DBHandler.getInstance().setError(uuid, 1060, "Invalid PDF - Provided PDF file does not contain any forms.");
                return;
            }

            DBHandler.getInstance().setCustomValue(uuid, "pageCount", String.valueOf(pageCount));
            DBHandler.getInstance().setCustomValue(uuid, "pagesConverted", "0");

        } catch (final PdfException e) {
            LOG.log(Level.SEVERE, "Invalid PDF", e);
            DBHandler.getInstance().setError(uuid, 1060, "Invalid PDF");
            return;
        }
        DBHandler.getInstance().setState(uuid, "processing");

        try {
            final File inFile = new File(inputDir + "/" + fileName);
            final HTMLConversionOptions htmlOptions = new HTMLConversionOptions(conversionParams);
            final FormViewerOptions viewerOptions = new FormViewerOptions(conversionParams);
            final PDFtoHTML5Converter html = new PDFtoHTML5Converter(inFile, outputDir, htmlOptions, viewerOptions);

            final long maxDuration = Long.parseLong(properties.getProperty(BaseServletContextListener.KEY_PROPERTY_MAX_CONVERSION_DURATION));
            html.setCustomErrorTracker(new ConversionTracker(uuid, maxDuration));

            html.convert();

            if ("1230".equals(DBHandler.getInstance().getStatus(uuid).get("errorCode"))) {
                final String message = String.format("Conversion %s exceeded max duration of %dms", uuid, maxDuration);
                LOG.log(Level.INFO, message);
                return;
            }

            ZipHelper.zipFolder(outputDirStr + "/" + fileNameWithoutExt,
                    outputDirStr + "/" + fileNameWithoutExt + ".zip");

            final String outputPathInDocroot = uuid + "/" + DefaultFileServlet.encodeURI(fileNameWithoutExt);

            DBHandler.getInstance().setCustomValue(uuid, "previewUrl", contextUrl + "/output/" + outputPathInDocroot + "/form.html");
            DBHandler.getInstance().setCustomValue(uuid, "downloadUrl", contextUrl + "/output/" + outputPathInDocroot + ".zip");

            final Storage storage = (Storage) getServletContext().getAttribute("storage");

            if (storage != null) {
                final String remoteUrl = storage.put(new File(outputDirStr + "/" + fileNameWithoutExt + ".zip"), fileNameWithoutExt + ".zip", uuid);
                DBHandler.getInstance().setCustomValue(uuid, "remoteUrl", remoteUrl);
            }

            DBHandler.getInstance().setState(uuid, "processed");

        } catch (final Throwable ex) {
            LOG.log(Level.SEVERE, "Exception thrown when converting input", ex);
            DBHandler.getInstance().setError(uuid, 1220, "Exception thrown when converting input" + ex.getMessage());
        }
    }

    /**
     * Validates the settings parameter passed to the request. It will parse the conversionParams,
     * validate them, and then set the params in the Individual object.
     *
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
        } catch (JsonParsingException exception) {
            doError(request, response, "Error encountered when parsing settings JSON <" + exception.getMessage() + '>', 400);
            return false;
        }

        try {
            FormVuSettingsValidator.validate(settings, false);
        } catch(final IllegalArgumentException e) {
            doError(request, response, e.getMessage(), 400);
            return false;
        }

        request.setAttribute("com.idrsolutions.microservice.settings", settings);

        return true;
    }
}
