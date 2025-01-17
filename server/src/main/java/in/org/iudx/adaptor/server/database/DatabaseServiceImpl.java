package in.org.iudx.adaptor.server.database;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import static in.org.iudx.adaptor.server.util.Constants.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabaseServiceImpl implements DatabaseService {

  private static final Logger LOGGER = LogManager.getLogger(DatabaseServiceImpl.class);
  private PostgresClient client;

  public DatabaseServiceImpl(PostgresClient postgresClient) {
    this.client = postgresClient;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DatabaseService getAdaptor(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {

    JsonArray response = new JsonArray();
    String username = request.getString(USERNAME);
    String id = request.getString(ADAPTOR_ID);
        
    String query;
    if(id != null) {
      query = GET_ONE_ADAPTOR.replace("$1", username).replace("$2", id);
    } else {
      query = GET_ALL_ADAPTOR.replace("$1", username);
    }
    
    client.executeAsync(query).onComplete(pgHandler -> {
      if (pgHandler.succeeded()) {
        RowSet<Row> result = pgHandler.result();
        for (Row row : result) {
          JsonObject rowJson = new JsonObject();
          JsonObject tempJson = row.toJson();
          JsonObject configData = tempJson.getJsonObject(DATA);
          
          rowJson.put(ID, tempJson.getValue("adaptor_id"))
                 .put(NAME, configData.getString(NAME))
                 .put(JAR_ID, tempJson.getString("jar_id"))
                 .put(SCHEDULE_PATTERN, configData.getString(SCHEDULE_PATTERN,null))
                 .put(JOB_ID, tempJson.getString("job_id"))
                 .put(LASTSEEN, tempJson.getString(TIMESTAMP))
                 .put(STATUS, tempJson.getString(STATUS));
          response.add(rowJson);
        }
        
        handler.handle(
            Future.succeededFuture(
                new JsonObject().put(STATUS, SUCCESS).put(ADAPTORS, response)));
      } else {
        LOGGER.error("Error: Database query failed; " + pgHandler.cause().getMessage());
        handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
      }
    });
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DatabaseService registerUser(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    
    String mode = request.getString(MODE);
    String query = null;
    if(mode.equals("POST")) {
      query = REGISTER_USER.replace("$1", request.getString(USERNAME))
                           .replace("$2", request.getString(PASSWORD))
                           .replace("$3", "active");
      
    } else if(mode.equals("PUT")) {
      query = UPDATE_USER_PASSWORD.replace("$1", request.getString(USERNAME))
                                  .replace("$2", request.getString(PASSWORD));
      
      if(request.containsKey(STATUS)) {
        query = UPDATE_USER.replace("$1", request.getString(USERNAME))
                           .replace("$2", request.getString(PASSWORD))
                           .replace("$3", request.getString(STATUS));
      }
      
    } else if (mode.equals(STATUS)) {
      query = UPDATE_USER_STATUS.replace("$1", request.getString(USERNAME))
                                .replace("$2", request.getString(STATUS));
    }
    
    client.executeAsync(query).onComplete(pgHandler -> {
      if (pgHandler.succeeded()) {
        if(pgHandler.result().rowCount() == 1) {
          LOGGER.debug("Info: Database query succeeded");
          handler.handle(Future.succeededFuture(new JsonObject().put(STATUS, SUCCESS))); 
        } else {
          LOGGER.error("Info: Database query failed; User not registered");
          handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
        }
      } else {
        LOGGER.error("Info: Database query failed; " + pgHandler.cause().getMessage());
        handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
      }
    });
    
    return this;
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  public DatabaseService getAdaptorUser(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    
    JsonArray response = new JsonArray();
    String query = null;
    String id = request.getString(ID,"");
    
    if(id != null && !id.isBlank()) {
      query = GET_USER.replace("$1", request.getString(ID));
    } else {
      query = GET_USERS;
    }
    
    client.executeAsync(query).onComplete(pgHandler -> {
      if (pgHandler.succeeded()) {
        RowSet<Row> result = pgHandler.result();
        for (Row row : result) {
          response.add(row.toJson());
        }
        
        handler.handle(
            Future.succeededFuture(
                new JsonObject().put(STATUS, SUCCESS).put("users", response)));
      } else {
        LOGGER.error("Error: Database query failed; " + pgHandler.cause().getMessage());
        handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
      }
    });
    
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DatabaseService authenticateUser(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    
    JsonArray response = new JsonArray();
    String query = AUTHENTICATE_USER
                      .replace("$1", request.getString(USERNAME))
                      .replace("$2",request.getString(PASSWORD));
    
    client.executeAsync(query).onComplete(pgHandler -> {
      if (pgHandler.succeeded()) {
        LOGGER.debug("Info: Database query succeeded");
        RowSet<Row> result = pgHandler.result();
        for (Row row : result) {
          response.add(row.toJson());
        }

        JsonObject queryRes = response.getJsonObject(0);
        if (queryRes.containsKey(EXISTS) && queryRes.getBoolean(EXISTS) == true) {
          handler.handle(Future.succeededFuture(new JsonObject().put(STATUS, SUCCESS)));
        } else {
          handler.handle(Future.succeededFuture(new JsonObject().put(STATUS, FAILED)));
        }
      } else {
        LOGGER.error("Error: Database query failed; " + pgHandler.cause().getMessage());
        handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
      }
    });

    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DatabaseService createAdaptor(JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    
    String username = request.getString(USERNAME);
    String adaptorId = request.getString(ADAPTOR_ID);
    String data = request.getString(DATA).replace("'", "\\\"");
    
    String query = CREATE_ADAPTOR
                      .replace("$1", adaptorId)
                      .replace("$3", username)
                      .replace("$4", COMPILING)
                      .replace("$2",data);
    
    client.executeAsync(query).onComplete(pgHandler -> {
      if (pgHandler.succeeded()) {
        LOGGER.debug("Info: Database query succeeded");
        handler.handle(Future.succeededFuture(new JsonObject().put(STATUS, SUCCESS)));

      } else {
        LOGGER.error("Info: Database query failed; " + pgHandler.cause().getMessage());
        handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
      }
    });

    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DatabaseService updateComplex(String query,
      Handler<AsyncResult<JsonObject>> handler) {
    
    LOGGER.debug("Info: Handling complex queries");
    
    client.executeAsync(query).onComplete(pgHandler -> {
      if (pgHandler.succeeded()) {
        LOGGER.debug("Info: Database query succeeded");
        handler.handle(Future.succeededFuture(new JsonObject().put(STATUS, SUCCESS)));
      } else {
        LOGGER.error("Info: Database query failed; " + pgHandler.cause().getMessage());
        handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
      }
    });
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DatabaseService deleteAdaptor(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    
    String username = request.getString(USERNAME);
    String id = request.getString(ADAPTOR_ID);
    
    String getQuery = GET_ONE_ADAPTOR.replace("$1", username).replace("$2", id);
    String deleteQuery = DELETE_ADAPTOR.replace("$1", id);
    
    LOGGER.debug("Info: Handling delete queries");
    
    client.executeAsync(getQuery).onComplete(pgHandler -> {
      if (pgHandler.succeeded()) {
        JsonObject tempJson = new JsonObject();
        RowSet<Row> result = pgHandler.result();
        if(result.size() == 1) {
          for(Row row: result) {
            tempJson.put(JAR_ID, row.toJson().getString("jar_id"));
            tempJson.put(STATUS, row.toJson().getString(STATUS));
          }
          
          String status = tempJson.getString(STATUS);
          String jarId = tempJson.getString(JAR_ID);
          if((jarId != null && !jarId.isBlank()) || !status.equals(COMPILING)) {
            if(status == null || !status.equals(RUNNING)) {
              client.executeAsync(deleteQuery).onComplete(deleteHandler ->{
                if(deleteHandler.succeeded()) {
                  LOGGER.debug("Info: Database query succeeded");
                  handler.handle(Future.succeededFuture(tempJson.put(STATUS, SUCCESS)));
                } else {
                  LOGGER.error("Error: Database query failed; " + pgHandler.cause().getMessage());
                  handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
                }
              }); 
            } else {
              LOGGER.error("Error: Already running instance; stop it before deletion");
              handler.handle(Future.failedFuture(new JsonObject().put(STATUS, ALREADY_RUNNING).toString()));
            }
          } else {
            LOGGER.error("Error: Codegen in process; Jar not submitted");
            handler.handle(Future.failedFuture(new JsonObject().put(STATUS, INCOMPLETE_CODEGEN).toString()));
          }
        } else {
          LOGGER.error("Error: Unable to delete");
          handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
        }
      } else {
        LOGGER.error("Error: Database query failed; " + pgHandler.cause().getMessage());
        handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
      }
    });
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DatabaseService syncAdaptorJob(String query, Handler<AsyncResult<JsonObject>> handler) {
    
    JsonArray response = new JsonArray();

    client.executeAsync(query).onComplete(pgHandler -> {
      if (pgHandler.succeeded()) {
        RowSet<Row> result = pgHandler.result();
        for (Row row : result) {
          response.add(row.toJson().getString("job_id"));
        }
        
        handler.handle(
            Future.succeededFuture(
                new JsonObject().put(JOBS, response)));
      } else {
        LOGGER.error("Error: Database query failed; " + pgHandler.cause().getMessage());
        handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED).toString()));
      }
    });

    return null;
  }
}
