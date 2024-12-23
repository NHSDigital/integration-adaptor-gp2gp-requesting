package uk.nhs.adaptors.gp2gpmhstestenv.mockspinemhsoutbound.filter;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class MutableHttpServletRequest extends HttpServletRequestWrapper {
    private final Map<String, String> customHeaders;
    private String customContentType;

    public MutableHttpServletRequest(HttpServletRequest request) {
        super(request);
        this.customHeaders = new HashMap<>();
    }

    @Override
    public String getContentType() {

        if (customContentType != null) {
            return customContentType;
        }

        return super.getRequest().getContentType();
    }

    public void setContentType(String contentType) {
        customContentType = contentType;
    }

    public void putHeader(String name, String value) {
        this.customHeaders.put(name, value);
    }

    public String getHeader(String name) {

        String headerValue = customHeaders.get(name);

        if (headerValue != null) {
            return headerValue;
        }

        return ((HttpServletRequest) getRequest()).getHeader(name);
    }

    public Enumeration<String> getHeaderNames() {

        Set<String> set = new HashSet<>(customHeaders.keySet());

        Enumeration<String> e = ((HttpServletRequest) getRequest()).getHeaderNames();
        while (e.hasMoreElements()) {
            String n = e.nextElement();
            set.add(n);
        }

        return Collections.enumeration(set);
    }

    public Enumeration<String> getHeaders(String name) {

        Set<String> headers = new HashSet<>();
        Enumeration<String> e = super.getHeaders(name);

        while (e.hasMoreElements()) {
            headers.add(e.nextElement());
        }

        if (customContentType != null && name.equals("content-type")) {
            headers.clear();
            headers.add(getContentType());
        }

        return Collections.enumeration(headers);
    }
}
