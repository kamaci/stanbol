package org.apache.stanbol.commons.httpqueryheaders.impl;

import static org.apache.stanbol.commons.httpqueryheaders.Constants.HEARDER_PREFIX;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.httpqueryheaders.OverwriteableHeaderHttpServletRequest;

@Component(immediate=true)
@Service(Filter.class)
@Property(name="pattern",value=".*")
public class QueryHeadersFilter implements Filter {
    
    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
                                                                                             ServletException {
        HttpServletRequest httpRequest;
        try {
            httpRequest = (HttpServletRequest) request;
        } catch (ClassCastException e) {
            // no Http request -> ignore
            chain.doFilter(request, response);
            return;
        }
        OverwriteableHeaderHttpServletRequest wrapped = null;
        Enumeration<String> paramNames = request.getParameterNames();
        while(paramNames.hasMoreElements()) {
            String param = paramNames.nextElement();
            if(param != null && param.startsWith(HEARDER_PREFIX) && param.length() > HEARDER_PREFIX.length()+1){
                String header = param.substring(HEARDER_PREFIX.length());
                String[] values = request.getParameterValues(param);
                if(values != null && values.length > 0){
                    if(wrapped == null ){ //lazzy initialisation 
                        wrapped = new OverwriteableHeaderHttpServletRequest(httpRequest);
                    }
                    wrapped.setHeader(header, values);
                }
            }
        }
        if(wrapped != null){
            chain.doFilter(wrapped, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Get properties parsed to the Filter
        //filterConfig
    }

}
