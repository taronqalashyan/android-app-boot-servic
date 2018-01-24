package rest.htpp.com.webprint;

import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import fi.iki.elonen.NanoHTTPD;
import rest.htpp.com.webprint.printer.Printer;

import static rest.htpp.com.webprint.constants.ServerConstants.ENDPOINT_TO_GET_FISCAL_STATUS;
import static rest.htpp.com.webprint.constants.ServerConstants.ENDPOINT_TO_GET_STATUS;
import static rest.htpp.com.webprint.constants.ServerConstants.ENDPOINT_TO_PRINT_CHART;
import static rest.htpp.com.webprint.constants.ServerConstants.ENDPOINT_TO_PRINT_JOBS;
import static rest.htpp.com.webprint.constants.ServerConstants.ENDPOINT_TO_PRINT_TEXT;
import static rest.htpp.com.webprint.constants.ServerConstants.MIME_XML;

/**
 * The android web server class to serve http POST requests with specified endpoints
 */
class AndroidWebServer extends NanoHTTPD {

    private static final String TAG = "AndroidWebServer";

    AndroidWebServer(int port) {
        super(port);
    }

    @Override
    public Response serve(final IHTTPSession session) {
        try {
            final Method method = session.getMethod();
            return serveFileRequest(session, method);
        } catch (IOException ioe) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                    "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
        } catch (ResponseException re) {
            return newFixedLengthResponse(re.getStatus(), MIME_PLAINTEXT, re.getMessage());
        } catch (Resources.NotFoundException nfe) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
        } catch (Exception ex) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML,
                    "<html><body><h1>Error</h1>" + ex.toString() + "</body></html>");
        }
    }

    //Helper Methods

    /**
     * Serves the file response with the necessary headers and content
     *
     * @param session the session to parse body
     * @param method  the method to handle request (POST)
     * @return the updated xml file response
     * @throws IOException
     * @throws ResponseException
     */
    private Response serveFileRequest(final IHTTPSession session, final Method method)
            throws IOException, ResponseException {
        if (Method.POST.equals(method)) {
            return collectResponse(session);
        }
        return null;
    }

    /**
     * Collects http response for given request
     *
     * @param session the session to parse body
     * @return response with corresponding xml file  and headers
     * @throws IOException
     * @throws ResponseException
     */
    private Response collectResponse(final IHTTPSession session)
            throws IOException, ResponseException {
        final Map<String, String> body = new HashMap<>();
        session.parseBody(body);
        final String route = session.getUri();
        final String contentType = detectMimeType(route);
        if (null == contentType) {
            return returnError(Response.Status.BAD_REQUEST, "Unsupported content type");
        }
        final Document xml = stringToXML(body.get("postData"));
        switch (route) {
            case ENDPOINT_TO_GET_STATUS:
                updateXmlFile(xml, "Get Status");
                break;
            case ENDPOINT_TO_PRINT_TEXT:
                updateXmlFile(xml, "Print Text");
                break;
            case ENDPOINT_TO_PRINT_CHART:
                updateXmlFile(xml, "Print Chart");
                break;
            case ENDPOINT_TO_PRINT_JOBS:
                final Map<Integer, String> printStatus = Printer.print(xml);
                return createPrinterResponse(printStatus, contentType);
            case ENDPOINT_TO_GET_FISCAL_STATUS:
                return createFiscalStatusResponse(contentType);
            default:
                return returnError(Response.Status.NOT_FOUND,
                        "The endpoint is not supported for 1.2 phase");
        }
        final String responseXML = xmlToString(xml);
        if (null == xml || null == responseXML || 0 == responseXML.length()) {
            return returnError(Response.Status.BAD_REQUEST, "Malformed or empty XML file");
        }
        final Response response = newFixedLengthResponse(Response.Status.OK, contentType, responseXML);
        response.addHeader("Content-Length", " " + responseXML.getBytes().length);
        return response;
    }

    /**
     * Updates xml document to append simple child element with custom value
     *
     * @param xml the provided document to update
     * @param msg the message to append to created child element
     */
    private void updateXmlFile(final Document xml, final String msg) {
        if (null == xml) {
            return;
        }
        final Element child = xml.createElement("child");
        child.setAttribute("name", "updatedByServer");
        child.setTextContent(msg);
        final Element root = xml.getDocumentElement();
        if (null != root) {
            root.appendChild(child);
        }
    }

    /**
     *
     * @param printStatus Printer status
     * @param contentType Response content type
     * @return Response for printer command
     */
    private Response createPrinterResponse(final Map<Integer, String> printStatus, final String contentType) {
        final Iterator it = printStatus.entrySet().iterator();
        final Map.Entry pair = (Map.Entry) it.next();
        Response.IStatus status = Response.Status.BAD_REQUEST;
        final String msg = pair.getValue().toString();
        if (Response.Status.OK.getRequestStatus() == (int) pair.getKey()) {
            status = Response.Status.OK;
        }
        final Response response = newFixedLengthResponse(status, contentType, msg);
        response.addHeader("Content-Length", " " + msg.getBytes().length);
        return response;
    }

    /**
     *
     * @param contentType Response content type.
     * @return
     */
    private Response createFiscalStatusResponse(String contentType) {

        return null;
    }

    /**
     * Converts string to xml
     *
     * @param body the provided string to convert
     * @return the xml document generated by string
     */
    private Document stringToXML(final String body) {
        Document xml;
        final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
            xml = dBuilder.parse(new InputSource(new StringReader(body)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
        return xml;
    }

    /**
     * Writes a bad request error response (HTTP/1.0 400-499) to the given output stream.
     *
     * @param errorCode the code
     * @param errorMsg  the message
     */
    private Response returnError(final Response.IStatus errorCode, final String errorMsg) {
        return newFixedLengthResponse(errorCode, MIME_PLAINTEXT, errorMsg);
    }

    /**
     * Detects the MIME type from the {@code fileName}.
     *
     * @param fileName The name of the file.
     * @return A MIME type.
     */
    private String detectMimeType(final String fileName) {
        if (!TextUtils.isEmpty(fileName) && fileName.endsWith(".xml")) {
            return MIME_XML;
        }
        return null;
    }

    /**
     * Converts xml to string by using Android native TransformerFactory
     *
     * @param doc provided document to convert
     * @return the result string
     */
    private static String xmlToString(final Document doc) {
        final TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer;
        try {
            transformer = tf.newTransformer();
            final StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.getBuffer().toString();
        } catch (TransformerException e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }
}