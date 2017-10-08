package com.wpff.common.auth;


import java.io.IOException;
import java.security.Principal;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.Response;

// Jedis
import redis.clients.jedis.Jedis;

/**
 * This is a container request filter that checks for an Authorization header
 * that should container a token that was generated by the AuthResource resource
 * (/auth/otken). This filter is only applied to resource methods that have been
 * annotated with TokenRequired (com.wpff.filter.TokenRequired).
 *
 * If a token that matches key/value in our database, we update the security
 * context with a UserPrincipal that has the name of the User.
 *
 */
public class TokenFilter implements ContainerRequestFilter {

  // Static Bearer text
  private static String BEARER = "Bearer";

  /**
   * Jedis instance used in the filter method to see if the token matches a user.
   */
  private Jedis jedis;

  /**
   * Create new request filter. Currently takes a jedis instance, will be replaced
   * with guice injection later.
   * 
   * @param jedis
   *          Jedis instance
   */
  public TokenFilter(Jedis jedis) {
    this.jedis = jedis;
  }

  /**
   * Filter an incoming request. Looks for the authorization header (starting with
   * 'Bearer') and if it matches a key/value in our DB, we update the context so
   * the resource method being called has that information
   *
   * @param requestContext
   *          Context that contains headers and will potentially be modified to
   *          have a new UserPrincipal.
   * @throws IOException
   *           If an error occurs of the caller is unauthorized.
   */
  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {

    String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

    if ((authHeader == null) || (!authHeader.startsWith(BEARER))) {
      throw new WebApplicationException(
          "Must supply valid Authorization header. Authenticate at /auth/token",
          Response.Status.UNAUTHORIZED);
    }

    // Grab token text from Header
    String token = authHeader.substring(BEARER.length() + 1);
    token = token.trim();

    // Get username and group from Jedis.
    String redisHashName = "user:" + token;
    
    final String username= jedis.hget(redisHashName, "name");
    final String group = jedis.hget(redisHashName, "group");

    if ((username == null) || (username.isEmpty())) {
      throw new WebApplicationException(
          "Must supply valid Authorization header. Authenticate at /auth/token",
          Response.Status.UNAUTHORIZED);
    }

    // Override the security context by giving it a new UserPrincipal
    // that will container the username we got from our DB
    requestContext.setSecurityContext(new SecurityContext() {
      @Override
      public Principal getUserPrincipal() {
        return new Principal() {
          @Override
          public String getName() {
            return username;
          }
        };
      }

      @Override
      public boolean isUserInRole(String role) {
        if (role.equals(group)) {
          return true;
        }
        else {
          return false;
        }
      }

      @Override
      public boolean isSecure() {
        return false;
      }

      @Override
      public String getAuthenticationScheme() {
        return null;
      }
    });
  }

}