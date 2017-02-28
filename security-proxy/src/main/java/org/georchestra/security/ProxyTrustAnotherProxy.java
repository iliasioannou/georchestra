package org.georchestra.security;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.georchestra.commons.configuration.GeorchestraConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

public class ProxyTrustAnotherProxy extends AbstractPreAuthenticatedProcessingFilter {

    private static String AUTH_HEADER = "sec-username";
    private static String CONFIG_KEY = "trustedProxy";
    private static final Log logger = LogFactory.getLog(ProxyTrustAnotherProxy.class.getPackage().getName());

    private GeorchestraConfiguration georchestraConfiguration;
    private Set<InetAddress> trustedProxies;

    public void init() throws UnknownHostException {
        String rawProxyValue = georchestraConfiguration.getProperty(CONFIG_KEY).trim();
        String[] rawProxyList;
        if(rawProxyValue.length() != 0){
            rawProxyList = rawProxyValue.split("\\s*,\\s*");
        } else {
            rawProxyList = new String[0];
        }

        this.trustedProxies = new HashSet<InetAddress>();

        for(String proxy : rawProxyList){
            InetAddress trustedProxyAddress = InetAddress.getByName(proxy);
            this.trustedProxies.add(trustedProxyAddress);
            logger.info("Add trusted proxy : " + trustedProxyAddress);
        }
        if(this.trustedProxies.size() == 0){
            logger.info("No trusted proxy loaded");
        } else {
            logger.info("Successful loading of " + this.trustedProxies.size() + " trusted proxy");
        }

        this.setContinueFilterChainOnUnsuccessfulAuthentication(true);
        //this.setAuthenticationDetailsSource(new ProxyTrustAnotherProxy.MyAuthenticationDetailsSource());
    }

    @Override
    protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {

        try {
            if(this.trustedProxies.contains(InetAddress.getByName(request.getRemoteAddr()))){
                String username = request.getHeader(AUTH_HEADER);
                logger.debug("Request from a trusted proxy, so log in user : " + username);
                request.getSession().setAttribute("pre-auth", true);
                return username;
            } else {
                logger.debug("Request from a NON trusted proxy, bypassing log in");
                return null;
            }
        } catch (UnknownHostException e) {
            logger.error("Unable to resolve remote address : " + request.getRemoteAddr());
            return null;
        }
    }

    @Override
    protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
        return "N/A";
    }

    public static class MyAuthenticationDetailsSource implements AuthenticationDetailsSource<HttpServletRequest, User> {


        @Override
        public User buildDetails(HttpServletRequest req) {

            req.getSession().setAttribute("pre-auth", true);

            String username = "joe";
            Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>();

            authorities.add(this.createGrantedAuthority("ROLE_USER"));
            authorities.add(this.createGrantedAuthority("ROLE_ADMINISTRATOR"));

            return new User(username, "N/A", authorities);
        }

        private GrantedAuthority createGrantedAuthority(final String role){
            return new GrantedAuthority() {
                @Override
                public String getAuthority() {
                    return role;
                }
            };
        }
    }

    public void setGeorchestraConfiguration(GeorchestraConfiguration georchestraConfiguration) {
        this.georchestraConfiguration = georchestraConfiguration;
    }

    public GeorchestraConfiguration getGeorchestraConfiguration() {
        return georchestraConfiguration;
    }
}