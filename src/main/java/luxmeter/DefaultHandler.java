package luxmeter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import javax.activation.MimetypesFileTypeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Objects;

import static luxmeter.Directory.listFiles;
import static org.apache.commons.lang3.EnumUtils.getEnum;

public class DefaultHandler implements HttpHandler {

    public static final int NO_BODY_CONTENT = -1;

    // allows you to see on first sight which requests are supported
    // additionally you don't need to bother with null checks (s. below)
    private enum RequestMethod {
        GET,
        HEAD
    }

    private final Path rootDir;

    public DefaultHandler(@Nonnull Path rootDir) {
        this.rootDir = rootDir;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Objects.requireNonNull(exchange);

        RequestMethod requestMethod = getEnum(RequestMethod.class, exchange.getRequestMethod().toUpperCase());

        validateRequestMethod(requestMethod);

        if (EnumSet.of(RequestMethod.HEAD, RequestMethod.GET).contains(requestMethod)) {
            Path absolutePath = getAbsoluteSystemPath(exchange.getRequestURI());
            File fileOrDirectory = absolutePath.toFile();

            validateFile(fileOrDirectory);

            if (fileOrDirectory.isDirectory()) {
                listDirectory(exchange, requestMethod, absolutePath);
            }
            // is file
            else {
                sendFileContent(exchange, requestMethod, fileOrDirectory);
            }
        }
    }

    private void validateRequestMethod(@Nullable RequestMethod requestMethod) {
        if (requestMethod == null) {
            throw new RequestException(HttpURLConnection.HTTP_BAD_REQUEST);
        }
    }

    private void validateFile(@Nonnull File fileOrDirectory) {
        if (!fileOrDirectory.exists()) {
            throw new RequestException(HttpURLConnection.HTTP_NOT_FOUND);
        }
    }

    private void sendFileContent(@Nonnull HttpExchange exchange,
                                 @Nonnull RequestMethod requestMethod,
                                 @Nonnull File fileOrDirectory) throws IOException {
        long responseLength = fileOrDirectory.length();
        String contentType = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(fileOrDirectory);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        if (requestMethod == RequestMethod.HEAD) {
            exchange.getResponseHeaders().add("Content-Length", ""+responseLength);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, NO_BODY_CONTENT);
        }
        // is GET
        else {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseLength);
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(fileOrDirectory))) {
                for (int data = in.read(); data != -1; data = in.read()) {
                    exchange.getResponseBody().write(data);
                }
            }
        }
    }

    private void listDirectory(@Nonnull HttpExchange exchange,
                               @Nonnull RequestMethod requestMethod,
                               @Nonnull Path absolutePath) throws IOException {
        Directory directory = listFiles(absolutePath);
        String output = directory.toString(rootDir);
        long responseLength = output.length();
        if (requestMethod == RequestMethod.HEAD) {
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.getResponseHeaders().add("Content-Length", ""+responseLength);
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, NO_BODY_CONTENT);
        }
        // is GET
        else {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, responseLength);
            exchange.getResponseBody().write(output.getBytes());
        }
    }

    private @Nonnull Path getAbsoluteSystemPath(@Nonnull URI uri) {
        String relativePath = uri.getPath().substring(1); // get rid of the leading '/'
        return rootDir.resolve(relativePath);
    }
}