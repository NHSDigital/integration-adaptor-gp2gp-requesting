package uk.nhs.adaptors.gp2gpmhstestenv.mockspinemhsoutbound.filter;

import static uk.nhs.adaptors.gp2gpmhstestenv.mockspinemhsoutbound.model.MessageHeaders.CONTENT_TYPE;
import static uk.nhs.adaptors.gp2gpmhstestenv.mockspinemhsoutbound.model.MessageHeaders.MESSAGE_ID;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import uk.nhs.adaptors.gp2gpmhstestenv.mockspinemhsoutbound.service.ContentTypeService;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor(onConstructor = @__(@Autowired))
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestResponseFilter extends OncePerRequestFilter {

    private ContentTypeService contentTypeService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        MutableHttpServletRequest mutableRequest = new MutableHttpServletRequest(request);

        Optional<String> headerOptional = Optional.ofNullable(mutableRequest.getHeader(CONTENT_TYPE));
        headerOptional.ifPresent(header -> {
            if (header.equals(ContentTypeService.MHS_OUTBOUND_CONTENT_TYPE)) {
                mutableRequest.putHeader(CONTENT_TYPE, ContentTypeService.AMENDED_CONTENT_TYPE);

                Optional<String> messageIdOptional = Optional.ofNullable(mutableRequest.getHeader(MESSAGE_ID));
                messageIdOptional.ifPresent(messageId -> contentTypeService.markAsAlteredContentType(messageId));
            }
        });

        Optional<String> contentTypeOptional = Optional.ofNullable(mutableRequest.getContentType());
        contentTypeOptional.ifPresent(contentType -> {
            if (contentType.equals(ContentTypeService.MHS_OUTBOUND_CONTENT_TYPE)) {
                mutableRequest.setContentType(ContentTypeService.AMENDED_CONTENT_TYPE);
            }
        });

        filterChain.doFilter(mutableRequest, response);
    }
}

