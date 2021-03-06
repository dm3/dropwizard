package com.yammer.dropwizard.jersey;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Splitter;
import com.yammer.dropwizard.jetty.UnbrandedErrorHandler;
import com.yammer.dropwizard.logging.Log;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.io.IOException;
import java.io.StringWriter;

public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {
    private static final Log LOG = Log.forClass(JsonProcessingExceptionMapper.class);
    private static final Splitter LINE_SPLITTER = Splitter.on("\n").trimResults();

    @Context
    private HttpServletRequest request;

    private final UnbrandedErrorHandler errorHandler;

    public JsonProcessingExceptionMapper() {
        this.errorHandler = new UnbrandedErrorHandler();
    }

    @Override
    public Response toResponse(JsonProcessingException exception) {
        /*
         * If the error is in the JSON generation, it's a server error.
         */
        if (exception instanceof JsonGenerationException) {
            LOG.warn(exception, "Error generating JSON");
            return Response.serverError().build();
        }

        final String message = exception.getMessage();

        /*
         * If we can't deserialize the JSON because someone forgot a no-arg constructor, it's a
         * server error and we should inform the developer.
         */
        if (message.startsWith("No suitable constructor found")) {
            LOG.error(exception, "Unable to deserialize the specific type");
            return Response.serverError().build();
        }

        /*
         * Otherwise, it's those pesky users.
         */
        try {
            LOG.debug(exception, "Unable to process JSON");
            final StringWriter writer = new StringWriter(4096);

            errorHandler.writeErrorPage(request,
                                        writer,
                                        400,
                                        stripLocation(message),
                                        false);
            return Response.status(Response.Status.BAD_REQUEST)
                           .type(MediaType.TEXT_HTML_TYPE)
                           .entity(writer.toString())
                           .build();
        } catch (IOException e) {
            LOG.debug(e, "Unable to output error message");
            return Response.serverError().build();
        }
    }

    @SuppressWarnings("LoopStatementThatDoesntLoop")
    private String stripLocation(String message) {
        for (String s : LINE_SPLITTER.split(message)) {
            return s;
        }
        return message;
    }
}
