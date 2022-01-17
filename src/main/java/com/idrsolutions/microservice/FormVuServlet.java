/*
 * FormVu Microservice Example
 *
 * Project Info: https://github.com/idrsolutions/formvu-microservice-example
 *
 * Copyright 2021 IDRsolutions
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
import com.idrsolutions.microservice.utils.DefaultFileServlet;
import com.idrsolutions.microservice.utils.SettingsValidator;
import com.idrsolutions.microservice.utils.ZipHelper;
import org.jpedal.PdfDecoderServer;
import org.jpedal.examples.html.PDFtoHTML5Converter;
import org.jpedal.exception.PdfException;
import org.jpedal.render.output.FormViewerOptions;
import org.jpedal.render.output.html.HTMLConversionOptions;

import javax.json.stream.JsonParsingException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
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
     * @param params The map of parameters that came with the request
     * @param inputFile The input file
     * @param outputDir The output directory of the converted file
     * @param contextUrl The context that this servlet is running in
     */
    @Override
    protected void convert(String uuid, Map<String, String[]> params,
                           File inputFile, File outputDir, String contextUrl) {

        final Map<String, String> conversionParams;
        try {
            Map<String, String> settings = DBHandler.INSTANCE.getSettings(uuid);
            conversionParams = settings != null ? settings : new HashMap<>();
        } catch (final SQLException e) {
            DBHandler.INSTANCE.setError(uuid, 500, "Database failure");
            return;
        }

        final String fileName = inputFile.getName();
        final String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
        final String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        // To avoid repeated calls to getParent() and getAbsolutePath()
        final String inputDir = inputFile.getParent();
        final String outputDirStr = outputDir.getAbsolutePath();

        if (!"pdf".equalsIgnoreCase(ext)) {
            DBHandler.INSTANCE.setError(uuid, 1070, "Internal error processing file - input file must be a PDF Form File");
            return;
        }

        //Makes the directory for the output file
        new File(outputDirStr + "/" + fileNameWithoutExt).mkdirs();

        DBHandler.INSTANCE.setState(uuid, "processing");

        try {
            final File inFile = new File(inputDir + "/" + fileName);
            final PdfDecoderServer decoder = new PdfDecoderServer(false);
            decoder.openPdfFile(inFile.getAbsolutePath());
            final boolean isForm = decoder.isForm();
            decoder.closePdfFile();
            decoder.dispose();

            if (!isForm) {
                throw new PdfException("Provided PDF file does not contain any forms.");
            }

            final HTMLConversionOptions htmlOptions = new HTMLConversionOptions(conversionParams);
            final FormViewerOptions viewerOptions = new FormViewerOptions(conversionParams);
            final PDFtoHTML5Converter html = new PDFtoHTML5Converter(inFile, outputDir, htmlOptions, viewerOptions);
            html.convert();

            ZipHelper.zipFolder(outputDirStr + "/" + fileNameWithoutExt,
                    outputDirStr + "/" + fileNameWithoutExt + ".zip");

            final String outputPathInDocroot = uuid + "/" + DefaultFileServlet.encodeURI(fileNameWithoutExt);

            DBHandler.INSTANCE.setCustomValue(uuid, "previewUrl", contextUrl + "/output/" + outputPathInDocroot + "/form.html");
            DBHandler.INSTANCE.setCustomValue(uuid, "downloadUrl", contextUrl + "/output/" + outputPathInDocroot + ".zip");

            DBHandler.INSTANCE.setState(uuid, "processed");

        } catch (final Throwable ex) {
            LOG.log(Level.SEVERE, "Exception thrown when converting input", ex);
            DBHandler.INSTANCE.setError(uuid, 1220, "Exception thrown when converting input" + ex.getMessage());
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

        final SettingsValidator settingsValidator = new SettingsValidator(settings);

        settingsValidator.validateBoolean("org.jpedal.pdf2html.addJavaScript", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.completeDocument", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.compressImages", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.compressSVG", false);
        settingsValidator.validateString("org.jpedal.pdf2html.containerId", ".*", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.convertPDFExternalFileToOutputType", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.convertSpacesToNbsp", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.disableComments", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.disableLinkGeneration", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.embedImagesAsBase64Stream", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.encryptTempFiles", false);
        settingsValidator.validateString("org.jpedal.pdf2html.fontsToRasterizeInTextMode", "((INCLUDE=)|(EXCLUDE=))(.*?(,|$))+", false);
        settingsValidator.validateString("org.jpedal.pdf2html.formTag", ".*", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.generateSearchFile", false);
        settingsValidator.validateFloat("org.jpedal.pdf2html.imageScale", new float[]{1, 10}, false);
        settingsValidator.validateString("org.jpedal.pdf2html.includedFonts", new String[]{"woff", "otf", "woff_base64", "otf_base64"}, false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.inlineSVG", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.keepGlyfsSeparate", false);
        settingsValidator.validateString("org.jpedal.pdf2html.logicalPageRange", "(\\s*((\\d+\\s*-\\s*\\d+)|(\\d+\\s*:\\s*\\d+)|(\\d+))\\s*(,|$)\\s*)+", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.omitNameDir", false);
        settingsValidator.validateString("org.jpedal.pdf2html.outputContentMode", validOutputContentModes, false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.outputThumbnails", false);
        settingsValidator.validateString("org.jpedal.pdf2html.password", ".*", false);
        settingsValidator.validateString("org.jpedal.pdf2html.realPageRange", "(\\s*((\\d+\\s*-\\s*\\d+)|(\\d+\\s*:\\s*\\d+)|(\\d+))\\s*(,|$)\\s*)+", false);
        settingsValidator.validateString("org.jpedal.pdf2html.scaling", "(\\d+\\.\\d+)|(\\d+x\\d+)|(fitWidth\\d+)|(fitHeight\\d+)|(\\d+)", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.separateTextToWords", false);
        settingsValidator.validateString("org.jpedal.pdf2html.textMode", validTextModeOptions, false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.useLegacyImageFileType", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.inlineJavaScriptAndCSS", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.noCheckboxOrRadioButtonImages", false);
        settingsValidator.validateString("org.jpedal.pdf2html.submitUrl", "[-a-zA-Z0-9@:%._\\+~#=]{1,256}([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.enableFDFJavaScript", false);
        settingsValidator.validateBoolean("org.jpedal.pdf2html.useFormVuAPI", false);

        if (!settingsValidator.isValid()) {
            doError(request, response, "Invalid settings detected.\n" + settingsValidator.getMessage(), 400);
            return false;
        }

        request.setAttribute("com.idrsolutions.microservice.settings", settings);

        return true;
    }
}
