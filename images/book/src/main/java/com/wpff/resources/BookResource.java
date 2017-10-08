package com.wpff.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

// utils
import org.apache.commons.beanutils.BeanUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpff.common.drop.filter.TokenRequired;
import com.wpff.common.result.ResultWrapper;
import com.wpff.common.result.ResultWrapperUtil;
import com.wpff.core.Book;
import com.wpff.db.BookDAO;
import com.wpff.query.BookQuery;
import com.wpff.result.BookResult;

import io.dropwizard.hibernate.UnitOfWork;
import io.dropwizard.jersey.params.IntParam;
// Swagger
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;


/**
 * Resource for the /book url. Manages books.
 */
@Api("/book")
@Path("/book")
@Produces(MediaType.APPLICATION_JSON)
public class BookResource {

  private final BookDAO bookDAO;

  public BookResource(BookDAO bookDAO) {
    this.bookDAO = bookDAO;
  }

  /**
   * Get a single book, by id.
   *
   * @param bookId
   *          ID of book
   * @param authDummy
   *          Dummy authorization string that is solely used for Swagger
   *          description.
   * @return Book
   */
  @ApiOperation(
    value="Get book by ID.",
    notes="Get book information. Requires authentication token in header with key AUTHORIZATION. " + 
    "Example: AUTHORIZATION: Bearer qwerty-1234-asdf-9876."
                )
  @GET
  @Path("/{id}")
  @UnitOfWork
  @TokenRequired
  public BookResult getBook(
    @ApiParam(value = "ID of book to retrieve.", required = false)
    @PathParam("id") 
    IntParam bookId,
    @ApiParam(value="Bearer authorization", required=true)
    @HeaderParam(value="Authorization") 
    String authDummy
                        ) {
    return this.convertToBean(authDummy, findSafely(bookId.get()));
  }



  /**
   * Get list of books.
   *
   * @param start
   *          Start index of data segment
   * @param segmentSize
   *          Size of data segment
   * @param titleQuery
   *          [optional] Name of book, or partial name, that is used to match
   *          against the database.
   * @param idQuery
   *          [optional] List of book ids.
   * @param authorIdQuery
   *          [optional] List of author ids.
   * @param authDummy
   *          Dummy authorization string that is solely used for Swagger
   *          description.
   * @return List of matching Books. When the params are empty, all books will be
   *         returned
   */
  @ApiOperation(value="Get books via optional 'title' query param or optional 'ids' query param. " + 
                "The three query params may be used at the same time.",
                response=BookResult.class, responseContainer="List",
                notes="Returns list of books. When no 'title', 'ids', or 'authorIds' are specified, all books in database are returned. " +
                "Requires authentication token in header with key AUTHORIZATION. Example: AUTHORIZATION: Bearer qwerty-1234-asdf-9876."
                )
  @GET
  @UnitOfWork
  @TokenRequired
  public ResultWrapper<BookResult> getBook(
    @ApiParam(value = "Title or partial title of book to retrieve.", required = false)
    @QueryParam("title") String titleQuery,
    
    @ApiParam(value = "List of book IDs to retrieve.", required = false)
    @QueryParam("id") List<Integer> idQuery,
    
    @ApiParam(value = "List of Author IDs to get books for.", required = false)
    @QueryParam("authorId") List<Integer> authorIdQuery,

    @ApiParam(value = "Where to start the returned data segment from the full result.", required = false) 
    @QueryParam("start") 
    Integer start,

    @ApiParam(value = "size of the returned data segment.", required = false) 
   	@QueryParam("segmentSize") 
		Integer segmentSize,
        
    @ApiParam(value="Bearer authorization", required=true)
    @HeaderParam(value="Authorization") String authDummy
                            ) {
    // Start
    
    // Using a Set to deal with any duplicates that might come up from using 
    // a 'title' and a 'id' that would overlap
    Set<Book> bookSet = new TreeSet<Book>();
    
    // When set to true, we won't return all books, just what we expected from the query
    boolean paramsExist = false;

    // Grab books by title, if it exists
    if (titleQuery != null) {
      System.out.println("Looking at title query: " + titleQuery);
      bookSet.addAll(bookDAO.findByName(titleQuery));
      paramsExist = true;
    }

    // The idQuery will be empty if nothing is specified, but will still exist as a List.
    if ( (idQuery != null) && (! idQuery.isEmpty()) ){
      System.out.println("Looking at id query: " + idQuery);
      bookSet.addAll(bookDAO.findById(idQuery));
      paramsExist = true;
    }

    // The authorIdQuery will be empty if nothing is specified, but will still exist as a List.
    if ( (authorIdQuery != null) && (! authorIdQuery.isEmpty()) ){
      System.out.println("Looking at author id query: " + authorIdQuery); 
      bookSet.addAll(bookDAO.findByAuthorId(authorIdQuery));
      paramsExist = true;
    }

    // If set of books is empty, grab all books
    if (bookSet.isEmpty() && (!paramsExist)) {
      System.out.println("bookSet is empty. adding all");
      bookSet.addAll(bookDAO.findAll());
    }

    // Convert the set of Books to list of BookResults
    List<BookResult> bookList = bookSet.
        stream().
        sorted().
        map( x -> this.convertToBean(authDummy, x)).
        collect(Collectors.toList());
    
    ResultWrapper<BookResult> result = ResultWrapperUtil.createWrapper(bookList, start, segmentSize);
        
    return result;
  }


  /**
   * Create a new book
   *
   * @param bookBean
   *          Book data
   * @param context
   *          security context (INJECTED via TokenFilter)
   * @param authDummy
   *          Dummy authorization string that is solely used for Swagger
   *          description.
   * @return newly created Book
   */
  @ApiOperation(
    value = "Create new book.",
    notes = "Creates new book. Requires authentication token in header with key AUTHORIZATION. "
        + "Example: AUTHORIZATION: Bearer qwerty-1234-asdf-9876.",
    response = Book.class
                )
  @POST
  @UnitOfWork(transactional = false)
  @TokenRequired
  @ApiResponse(code = 409, message = "Duplicate value")
  public BookResult createBook(
    @ApiParam(value = "Book information.", required = true)
    BookQuery bookBean,
    @Context SecurityContext context,
    @ApiParam(value="Bearer authorization", required=true)
    @HeaderParam(value="Authorization") String authDummy
                           ) {
    // START
    try {
       // Verify context's group is admin.
      verifyAdminUser(context);
      
      // Make new Book from bookBean (which is a PostBook)
      Book bookInDatabase = new Book();
      // copy(destination, source)
      BeanUtils.copyProperties(bookInDatabase, bookBean);
      
      // the bookBean's subjects is a list, convert it into a CSV string
      BeanUtils.copyProperty(bookInDatabase, "subject", convertListToCsv(bookBean.getSubjects()));
      
      // the bookBean's isbns is a list, convert it into a CSV string
      BeanUtils.copyProperty(bookInDatabase, "isbn", convertListToCsv(bookBean.getIsbns()));

      // year is different too
      BeanUtils.copyProperty(bookInDatabase, "year", bookBean.getFirstPublishedYear()); 
      
      // open library url is different too
      BeanUtils.copyProperty(bookInDatabase, "olWorks", bookBean.getOpenlibraryWorkUrl());
      
      return this.convertToBean(authDummy, bookDAO.create(bookInDatabase));
    }
    catch (org.hibernate.exception.ConstraintViolationException e) {
      String errorMessage = e.getMessage();
      // check cause/parent
      if (e.getCause() != null) {
        errorMessage = e.getCause().getMessage();
      }

      throw new WebApplicationException(errorMessage, 409);
    }
    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException bean) {
      throw new WebApplicationException("Error in updating database when creating book  " + bookBean + ".", Response.Status.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Helper to convert a list into a csv of those values
   * 
   * @param values
   * @return
   */
  static String convertListToCsv(List<String> values) {
      String csvString = "";
      for (String s : values) {
        csvString += s + ",";
      }
      // trim last comma
      csvString = csvString.substring(0, csvString.length());
      return csvString;
  }

  /**
   * Deletes a book by ID
   *
   * @param bookId ID of book
   * @param context security context (INJECTED via TokenFilter)
   * @param authDummy Dummy authorization string that is solely used for Swagger description.
   * @return Response denoting if the operation was successful (202) or failed (404)
   */
  @ApiOperation(
    value="Delete book by ID.",
    notes="Delete book from database. Requires authentication token in header with key AUTHORIZATION."
        + " Example: AUTHORIZATION: Bearer qwerty-1234-asdf-9876."
                )
  @DELETE
  @Path("/{id}")
  @UnitOfWork
  @TokenRequired
  public Response deleteBook(
    @ApiParam(value = "ID of book to retrieve.", required = true)
    @PathParam("id") 
    IntParam bookId,
    @Context SecurityContext context,
    @ApiParam(value="Bearer authorization", required=true)
    @HeaderParam(value="Authorization") 
    String authDummy
                        ) {
    // Start
    try {
      // Verify context's name is admin.
      verifyAdminUser(context);

      // Is OK to remove book
      bookDAO.delete(findSafely(bookId.get()));
      return Response.ok().build();

    }
    catch (org.hibernate.HibernateException he) {
      throw new NotFoundException("No book by id '" + bookId + "'");
    }
  }

  

  /************************************************************************/
  /** Helper methods **/
  /************************************************************************/

  /**
   * Convert a Book from the DB into a BookResult for return to caller
   * 
   * @param dbBook
   *          Book in DB
   * @return Book bean
   */
  private BookResult convertToBean(String authString, Book dbBook) {
    BookResult result = new BookResult();

    try {
      System.out.println("Converting: " + dbBook);
      BeanUtils.copyProperties(result, dbBook);
      
      // open library url
      BeanUtils.copyProperty(result, "openlibraryWorkUrl", dbBook.getOlWorks());

      // published year
      BeanUtils.copyProperty(result, "firstPublishedYear", dbBook.getYear());

      // NOTE:
      // the dbBook has 'getSubject' and 'getIsbn', both singular,
      // while the result bean has 'getSubjects' and 'getIsbns', both plural.
      // If they had the same name, the above copyProperties would die as 
      // it's copying a List to a String and vica versa.
      
      // dbBook's 'subjects' is a csv, convert into a list
      if (dbBook.getSubject() != null) {
        List<String> subjects = Arrays.asList(dbBook.getSubject().split("\\s*,\\s*"));
        BeanUtils.copyProperty(result, "subjects", subjects);
      }
      
      // dbBook's 'isbn' is a csv. Convert into a list
      List<String> isbns = Arrays.asList(dbBook.getIsbn().split("\\s*,\\s*"));
      BeanUtils.copyProperty(result, "isbns", isbns);

      // Get author name now
      String authorName = getAuthorName(authString, dbBook.getAuthorId());
      BeanUtils.copyProperty(result, "authorName", authorName);      
    } 
    catch (IllegalAccessException | InvocationTargetException e) {
          e.printStackTrace();
    }
    
    return result; 
  }

  /**
   * Retrieve the author name from the 'author' webservice for the incoming
   * authorId
   * 
   * @param authString
   *          Authentication header which is necessary for a REST call to 'author'
   *          web service
   * @param authorId
   *          ID of author to get name for
   * @return
   */
  private String getAuthorName(String authString, int authorId) {
    try {
      // Going to the 'author' web service directly
      String url = "http://author:8080/author/" + authorId;
      System.out.println("Getting authorname from URL:" + url);
      
      HttpClient client = HttpClientBuilder.create().build();
      HttpGet request = new HttpGet(url);

      // add request header
      request.addHeader("User-Agent", "BookAgent");
      request.addHeader("content-type", "application/json");
      request.addHeader("Authorization", authString);
      
      // Execute request
      HttpResponse response = client.execute(request);

      // Get code
      int responseCode = response.getStatusLine().getStatusCode();
      
      // Convert body of result
      BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
      StringBuffer result = new StringBuffer();
      String line = "";
      while ((line = rd.readLine()) != null) {
        result.append(line);
      }
      
      String authorName = "";

      // Check result
      if (responseCode == 200) {
        // Convert into bean
        ObjectMapper mapper = new ObjectMapper();
        AuthorBean authorBean = null;
        try {
          authorBean = mapper.readValue(result.toString(), AuthorBean.class);
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }
        
        authorName = authorBean.getName();
      }
      else {
        System.out.println("Unable to get author name for id: " + authorId);
        System.out.println("Error code: " + responseCode);
        System.out.println("Error content: " + result);
      }

      return authorName;
    } catch (Exception e) {
      e.printStackTrace();
      return "";
    }
  }

  /**
   * Look for book by incoming id. If returned Book is null, throw 404.
   *
   * @param id ID of book to look for
   */
  private Book findSafely(int id) {
    return bookDAO.findById(id).orElseThrow(() -> new NotFoundException("No book by id " + id));
  }
  
  
  /**
   * Verifies the incoming user is in group "admin"
   * 
   * Throws exception if user is not admin.
   */
  static void verifyAdminUser(SecurityContext context) throws WebApplicationException {
    if (! context.isUserInRole("admin")) {
      throw new WebApplicationException("Must be logged in as a member of the 'admin' user group.", Response.Status.UNAUTHORIZED);
    }
  }

  
}
