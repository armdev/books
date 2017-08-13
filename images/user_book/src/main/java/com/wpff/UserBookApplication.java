package com.wpff;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

// swagger
import io.federecio.dropwizard.swagger.*;

// hibernate
import io.dropwizard.Application;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.hibernate.UnitOfWorkAwareProxyFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.views.ViewBundle;

// Exception mapping
import org.apache.commons.lang3.exception.ExceptionUtils;
import javax.persistence.PersistenceException;
import java.util.HashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ExceptionMapper;

// Jedis
import com.bendb.dropwizard.redis.JedisBundle;
import com.bendb.dropwizard.redis.JedisFactory;
import redis.clients.jedis.Jedis;

import javax.ws.rs.container.DynamicFeature;

// Resources
import com.wpff.resources.UserBookResource;
import com.wpff.core.Tag;
import com.wpff.core.UserBook;
import com.wpff.core.User;
import com.wpff.db.TagDAO;
import com.wpff.db.UserBookDAO;
import com.wpff.db.UserDAO;
import com.wpff.filter.TokenRequiredFeature;


/**
 * Application for managing tags
 *
 */
public class UserBookApplication extends Application<UserBookConfiguration> {

  public static void main(final String[] args) throws Exception {
    new UserBookApplication().run(args);
  }

  @Override
  public String getName() {
    return "UserBook web-service";
  }


  // Create hibernate bundle
  private final HibernateBundle<UserBookConfiguration> hibernateBundle =
// https://stackoverflow.com/questions/29614205/org-hibernate-annotationexception-use-of-onetomany-or-manytomany-targeting-an
      new HibernateBundle<UserBookConfiguration>(UserBook.class, Tag.class, User.class) {
        @Override
        public DataSourceFactory getDataSourceFactory(UserBookConfiguration configuration) {
          return configuration.getDataSourceFactory();
        }
      };


  /**
   * Initialize the application
   *
   */
  @Override
  public void initialize(final Bootstrap<UserBookConfiguration> bootstrap) {
    // Hibernate
    bootstrap.addBundle(hibernateBundle);

    // Swagger
    bootstrap.addBundle(new SwaggerBundle<UserBookConfiguration>() {
        @Override
        protected SwaggerBundleConfiguration getSwaggerBundleConfiguration(UserBookConfiguration configuration) {
          return configuration.swaggerBundleConfiguration;
        }
      });


    // Jedis for Redis
    bootstrap.addBundle(new JedisBundle<UserBookConfiguration>() {
        @Override
        public JedisFactory getJedisFactory(UserBookConfiguration configuration) {
          return configuration.getJedisFactory();
        }
      });
  }

  @Override
  public void run(final UserBookConfiguration configuration,
                  final Environment environment) {
    // Set up Jedis. Currently JedisFactory doesn't inject into a filter, just Resources.
    // TODO: look at Guice.
    Jedis jedis = configuration.getJedisFactory().build(environment).getResource();

    // UserBook DAO 
    final UserBookDAO userBookDao = new UserBookDAO(hibernateBundle.getSessionFactory());
    final UserDAO userDao = new UserDAO(hibernateBundle.getSessionFactory());
    final TagDAO tagDao = new TagDAO(hibernateBundle.getSessionFactory());

    // Register endpoints
    environment.jersey().register(new UserBookResource(userDao, tagDao, userBookDao));

    environment.jersey().register(new PersistenceExceptionMapper());

    // Add a container request filter for securing webservice endpoints.
    DynamicFeature tokenRequired =new TokenRequiredFeature(jedis) ;
    environment.jersey().register(tokenRequired);
  }

}


/**
 * Mapper to convert peristence exceptions into something more readable.
 * 
 */
class PersistenceExceptionMapper implements ExceptionMapper<PersistenceException> {
    @Override
    public Response toResponse(final PersistenceException e) {
      final String rootMessage = ExceptionUtils.getRootCauseMessage(e);
      System.out.println("Got exception from database: " + rootMessage);

      if (rootMessage.contains("Duplicate entry")) {
        return Response.status(409)
            .type(MediaType.APPLICATION_JSON_TYPE)
            .entity(new HashMap<String, String>() { {
              put("error", "Entity already exists, please update your query and try again.");
            } }).build();
      }

      // Create a JSON response with the provided hashmap
      return Response.status(500)
          .type(MediaType.APPLICATION_JSON_TYPE)
          .entity(new HashMap<String, String>() { {
            put("error", rootMessage);
          } }).build();
    }
}
