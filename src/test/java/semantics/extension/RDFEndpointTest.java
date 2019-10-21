package semantics.extension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.neo4j.helpers.collection.Iterators.count;
import static org.neo4j.server.ServerTestUtils.getSharedTestTemporaryFolder;
import static semantics.RDFImport.PREFIX_SEPARATOR;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilder;
import org.neo4j.harness.TestServerBuilders;
import org.neo4j.harness.junit.Neo4jRule;
import org.neo4j.kernel.configuration.ssl.LegacySslPolicyConfig;
import org.neo4j.server.ServerTestUtils;
import org.neo4j.test.server.HTTP;
import semantics.ModelTestUtils;
import semantics.RDFImport;
import semantics.RDFImportTest;
import semantics.mapping.MappingUtils;

/**
 * Created by jbarrasa on 14/09/2016.
 */
public class RDFEndpointTest {

  @Rule
  public Neo4jRule neo4j = new Neo4jRule()
      .withProcedure(RDFImport.class).withFunction(RDFImport.class)
      .withProcedure(MappingUtils.class);

  private static final ObjectMapper jsonMapper = new ObjectMapper();

  private static final CollectionType collectionType = TypeFactory
      .defaultInstance().constructCollectionType(Set.class, Map.class);

  @Test
  public void testGetNodeById() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String ontoCreation = "MERGE (p:Category {catName: ' Person'})\n" +
                "MERGE (a:Category {catName: 'Actor'})\n" +
                "MERGE (d:Category {catName: 'Director'})\n" +
                "MERGE (c:Category {catName: 'Critic'})\n" +
                "CREATE (a)-[:SCO]->(p)\n" +
                "CREATE (d)-[:SCO]->(p)\n" +
                "CREATE (c)-[:SCO]->(p)\n" +
                "RETURN *";
            graphDatabaseService.execute(ontoCreation);
            String dataInsertion = "CREATE (Keanu:Actor {name:'Keanu Reeves', born:1964})\n" +
                "CREATE (Carrie:Director {name:'Carrie-Anne Moss', born:1967})\n" +
                "CREATE (Laurence:Director {name:'Laurence Fishburne', born:1961})\n" +
                "CREATE (Hugo:Critic {name:'Hugo Weaving', born:1960})\n" +
                "CREATE (AndyW:Actor {name:'Andy Wachowski', born:1967})\n" +
                "CREATE (Hugo)-[:WORKS_WITH]->(AndyW)\n" +
                "CREATE (Hugo)<-[:FRIEND_OF]-(Carrie)";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {
      // When
      Result result = server.graph().execute("MATCH (n:Critic) RETURN id(n) AS id ");
      Long id = (Long) result.next().get("id");
      assertEquals(new Long(7), id);

      // When
      HTTP.Response response = HTTP.withHeaders("Accept", "application/ld+json").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "describe/id/"
              + id.toString());

      String expected = "[ {\n"
          + "  \"@id\" : \"neo4j://individuals#5\",\n"
          + "  \"neo4j://vocabulary#FRIEND_OF\" : [ {\n"
          + "    \"@id\" : \"neo4j://individuals#7\"\n"
          + "  } ]\n"
          + "}, {\n"
          + "  \"@id\" : \"neo4j://individuals#7\",\n"
          + "  \"@type\" : [ \"neo4j://vocabulary#Critic\" ],\n"
          + "  \"neo4j://vocabulary#WORKS_WITH\" : [ {\n"
          + "    \"@id\" : \"neo4j://individuals#8\"\n"
          + "  } ],\n"
          + "  \"neo4j://vocabulary#born\" : [ {\n"
          + "    \"@type\" : \"http://www.w3.org/2001/XMLSchema#long\",\n"
          + "    \"@value\" : \"1960\"\n"
          + "  } ],\n"
          + "  \"neo4j://vocabulary#name\" : [ {\n"
          + "    \"@value\" : \"Hugo Weaving\"\n"
          + "  } ]\n"
          + "} ]";
      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.JSONLD, response.rawContent(), RDFFormat.JSONLD));

    }
  }


  @Test
  public void ImportGetNodeById() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String ontoCreation = "MERGE (p:Category {catName: ' Person'})\n" +
                "MERGE (a:Category {catName: 'Actor'})\n" +
                "MERGE (d:Category {catName: 'Director'})\n" +
                "MERGE (c:Category {catName: 'Critic'})\n" +
                "CREATE (a)-[:SCO]->(p)\n" +
                "CREATE (d)-[:SCO]->(p)\n" +
                "CREATE (c)-[:SCO]->(p)\n" +
                "RETURN *";
            graphDatabaseService.execute(ontoCreation);
            String dataInsertion = "CREATE (Keanu:Actor {name:'Keanu Reeves', born:1964})\n" +
                "CREATE (Carrie:Director {name:'Carrie-Anne Moss', born:1967})\n" +
                "CREATE (Laurence:Director {name:'Laurence Fishburne', born:1961})\n" +
                "CREATE (Hugo:Critic {name:'Hugo Weaving', born:1960})\n" +
                "CREATE (AndyW:Actor {name:'Andy Wachowski', born:1967})\n" +
                "CREATE (Hugo)-[:WORKS_WITH]->(AndyW)\n" +
                "CREATE (Hugo)<-[:FRIEND_OF]-(Carrie)";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {
      // When
      Result result = server.graph().execute("MATCH (n:Critic) RETURN id(n) AS id ");
      Long id = (Long) result.next().get("id");
      assertEquals(new Long(7), id);

      try (Driver driver = GraphDatabase.driver(neo4j.boltURI(),
          Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE)
              .toConfig()); Session session = driver.session()) {
        session.run("CREATE INDEX ON :Resource(uri)");
        StatementResult importResults
            = session.run("CALL semantics.importRDF('" +
            HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "describe/id/"
            + id.toString() +
            "','Turtle',{ handleVocabUris: 'IGNORE', typesToLabels: true, commitSize: 500})");

        Map<String, Object> singleResult = importResults
            .single().asMap();

        assertEquals(5L, singleResult.get("triplesLoaded"));
        StatementResult postImport = session.run("MATCH (n:Critic) RETURN n");
        Node criticPostImport = postImport.next().get("n").asNode();
        result = server.graph().execute("MATCH (n:Critic) "
            + "RETURN n.born as born, n.name as name");
        Map<String, Object> criticPreImport = result.next();
        assertEquals(criticPreImport.get("name"),criticPostImport.get("name").asString());
        assertEquals(criticPreImport.get("born"),criticPostImport.get("born").asLong());

      }

    }
  }


  @Test
  public void ImportGetCypher() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String ontoCreation = "MERGE (p:Category {catName: ' Person'})\n" +
                "MERGE (a:Category {catName: 'Actor'})\n" +
                "MERGE (d:Category {catName: 'Director'})\n" +
                "MERGE (c:Category {catName: 'Critic'})\n" +
                "CREATE (a)-[:SCO]->(p)\n" +
                "CREATE (d)-[:SCO]->(p)\n" +
                "CREATE (c)-[:SCO]->(p)\n" +
                "RETURN *";
            graphDatabaseService.execute(ontoCreation);
            String dataInsertion = "CREATE (Keanu:Actor {name:'Keanu Reeves', born:1964})\n" +
                "CREATE (Carrie:Director {name:'Carrie-Anne Moss', born:1967})\n" +
                "CREATE (Laurence:Director {name:'Laurence Fishburne', born:1961})\n" +
                "CREATE (Hugo:Critic {name:'Hugo Weaving', born:1960})\n" +
                "CREATE (AndyW:Actor {name:'Andy Wachowski', born:1967})\n" +
                "CREATE (Hugo)-[:WORKS_WITH]->(AndyW)\n" +
                "CREATE (Hugo)<-[:FRIEND_OF]-(Carrie)";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {
      // When
      Result result = server.graph().execute("MATCH (n:Critic) RETURN id(n) AS id ");
      Long id = (Long) result.next().get("id");
      assertEquals(new Long(7), id);

      try (Driver driver = GraphDatabase.driver(neo4j.boltURI(),
          Config.build().withEncryptionLevel(Config.EncryptionLevel.NONE)
              .toConfig()); Session session = driver.session()) {
        session.run("CREATE INDEX ON :Resource(uri)");

        StatementResult importResults
            = session.run("CALL semantics.importRDF('" +
            HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypher"  +
            "','Turtle',{ handleVocabUris: 'IGNORE', "
            + "payload: '{ \"cypher\": \"MATCH (x:Critic) RETURN x \"}'})");

        Map<String, Object> singleResult = importResults
            .single().asMap();

        assertEquals(3L, singleResult.get("triplesLoaded"));
        StatementResult postImport = session.run("MATCH (n:Critic) RETURN n");
        Node criticPostImport = postImport.next().get("n").asNode();
        result = server.graph().execute("MATCH (n:Critic) "
            + "RETURN n.born as born, n.name as name");
        Map<String, Object> criticPreImport = result.next();
        assertEquals(criticPreImport.get("name"),criticPostImport.get("name").asString());
        assertEquals(criticPreImport.get("born"),criticPostImport.get("born").asLong());

      }

    }
  }

  @Test
  public void testFindNodeByLabelAndProperty() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String ontoCreation = "MERGE (p:Category {catName: ' Person'})\n" +
                "MERGE (a:Category {catName: 'Actor'})\n" +
                "MERGE (d:Category {catName: 'Director'})\n" +
                "MERGE (c:Category {catName: 'Critic'})\n" +
                "CREATE (a)-[:SCO]->(p)\n" +
                "CREATE (d)-[:SCO]->(p)\n" +
                "CREATE (c)-[:SCO]->(p)\n" +
                "RETURN *";
            graphDatabaseService.execute(ontoCreation);
            String dataInsertion = "CREATE (Keanu:Actor {name:'Keanu Reeves', born:1964})\n" +
                "CREATE (Carrie:Director {name:'Carrie-Anne Moss', born:1967})\n" +
                "CREATE (Laurence:Director {name:'Laurence Fishburne', born:1961})\n" +
                "CREATE (Hugo:Critic {name:'Hugo Weaving', born:1960})\n" +
                "CREATE (AndyW:Actor {name:'Andy Wachowski', born:1964})\n" +
                "CREATE (Hugo)-[:WORKS_WITH]->(AndyW)\n" +
                "CREATE (Hugo)<-[:FRIEND_OF]-(Carrie)";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {
      // When
      Result result = server.graph().execute("MATCH (n:Critic) RETURN id(n) AS id ");
      Long id = (Long) result.next().get("id");
      assertEquals(new Long(7), id);

      // When
      HTTP.Response response = HTTP.withHeaders("Accept", "application/ld+json").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/find/Director/born/1961?valType=INTEGER");

      String expected = "[ {\n"
          + "  \"@id\" : \"neo4j://individuals#6\",\n"
          + "  \"@type\" : [ \"neo4j://vocabulary#Director\" ],\n"
          + "  \"neo4j://vocabulary#born\" : [ {\n"
          + "    \"@type\" : \"http://www.w3.org/2001/XMLSchema#long\",\n"
          + "    \"@value\" : \"1961\"\n"
          + "  } ],\n"
          + "  \"neo4j://vocabulary#name\" : [ {\n"
          + "    \"@value\" : \"Laurence Fishburne\"\n"
          + "  } ]\n"
          + "} ]";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.JSONLD, response.rawContent(), RDFFormat.JSONLD));

      // When
      response = HTTP.withHeaders("Accept", "application/ld+json").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/find/Director/name/Laurence%20Fishburne");

      expected = "[ {\n"
          + "  \"@id\" : \"neo4j://individuals#6\",\n"
          + "  \"@type\" : [ \"neo4j://vocabulary#Director\" ],\n"
          + "  \"neo4j://vocabulary#born\" : [ {\n"
          + "    \"@type\" : \"http://www.w3.org/2001/XMLSchema#long\",\n"
          + "    \"@value\" : \"1961\"\n"
          + "  } ],\n"
          + "  \"neo4j://vocabulary#name\" : [ {\n"
          + "    \"@value\" : \"Laurence Fishburne\"\n"
          + "  } ]\n"
          + "} ]";
      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.JSONLD, response.rawContent(), RDFFormat.JSONLD));

      // When
      response = HTTP.withHeaders("Accept", "application/ld+json").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/find/Actor/born/1964?valType=INTEGER");

      expected = "[ {\n"
          + "  \"@id\" : \"neo4j://individuals#4\",\n"
          + "  \"@type\" : [ \"neo4j://vocabulary#Actor\" ],\n"
          + "  \"neo4j://vocabulary#born\" : [ {\n"
          + "    \"@type\" : \"http://www.w3.org/2001/XMLSchema#long\",\n"
          + "    \"@value\" : \"1964\"\n"
          + "  } ],\n"
          + "  \"neo4j://vocabulary#name\" : [ {\n"
          + "    \"@value\" : \"Keanu Reeves\"\n"
          + "  } ]\n"
          + "}, {\n"
          + "  \"@id\" : \"neo4j://individuals#7\",\n"
          + "  \"neo4j://vocabulary#WORKS_WITH\" : [ {\n"
          + "    \"@id\" : \"neo4j://individuals#8\"\n"
          + "  } ]\n"
          + "}, {\n"
          + "  \"@id\" : \"neo4j://individuals#8\",\n"
          + "  \"@type\" : [ \"neo4j://vocabulary#Actor\" ],\n"
          + "  \"neo4j://vocabulary#born\" : [ {\n"
          + "    \"@type\" : \"http://www.w3.org/2001/XMLSchema#long\",\n"
          + "    \"@value\" : \"1964\"\n"
          + "  } ],\n"
          + "  \"neo4j://vocabulary#name\" : [ {\n"
          + "    \"@value\" : \"Andy Wachowski\"\n"
          + "  } ]\n"
          + "} ]";
      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.JSONLD, response.rawContent(), RDFFormat.JSONLD));
    }
  }


  @Test
  public void testGetNodeByIdNotFoundOrInvalid() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class).newServer()) {
      HTTP.Response response = HTTP.withHeaders("Accept", "application/ld+json").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/id/9999999");

      assertEquals("[ ]", response.rawContent());
      assertEquals(200, response.status());

      //TODO: Non Long param for ID (would be a good idea to be consistent with previous case?...)
      response = HTTP.withHeaders("Accept", "application/ld+json").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/id/adb");

      assertEquals("", response.rawContent());
      assertEquals(404, response.status());

    }
  }

  @Test
  public void testFindNodeByLabelAndPropertyNotFoundOrInvalid() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class).newServer()) {
      HTTP.Response response = HTTP.withHeaders("Accept", "application/ld+json").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/find/WrongLabel/wrongProperty/someValue");

      assertEquals("[ ]", response.rawContent());
      assertEquals(200, response.status());

      response = HTTP.withHeaders("Accept", "application/ld+json").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/find/Something");

      assertEquals("", response.rawContent());
      assertEquals(404, response.status());

    }
  }

  @Test
  public void testGetNodeByUriNotFoundOrInvalid() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class).newServer()) {
      HTTP.Response response = HTTP.withHeaders("Accept", "application/ld+json").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/9999999");

      assertEquals("[ ]", response.rawContent());
      assertEquals(200, response.status());

    }
  }

  @Test
  public void testPing() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class).newServer()) {
      HTTP.Response response = HTTP.GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "ping");

      assertEquals("{\"ping\":\"here!\"}", response.rawContent());
      assertEquals(200, response.status());

    }
  }

  @Test
  public void testCypherOnLPG() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String ontoCreation = "MERGE (p:Category {catName: 'Person'})\n" +
                "MERGE (a:Category {catName: 'Actor'})\n" +
                "MERGE (d:Category {catName: 'Director'})\n" +
                "MERGE (c:Category {catName: 'Critic'})\n" +
                "CREATE (a)-[:SCO]->(p)\n" +
                "CREATE (d)-[:SCO]->(p)\n" +
                "CREATE (c)-[:SCO]->(p)\n" +
                "RETURN *";
            graphDatabaseService.execute(ontoCreation);
            String dataInsertion = "CREATE (Keanu:Actor {name:'Keanu Reeves', born:1964})\n" +
                "CREATE (Carrie:Director {name:'Carrie-Anne Moss', born:1967})\n" +
                "CREATE (Laurence:Director {name:'Laurence Fishburne', born:1961})\n" +
                "CREATE (Hugo:Critic {name:'Hugo Weaving', born:1960})\n" +
                "CREATE (AndyW:Actor {name:'Andy Wachowski', born:1967})";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {

      Result result = server.graph().execute("MATCH (n:Critic) RETURN id(n) AS id ");
      assertEquals(1, count(result));

      Map<String, Object> map = new HashMap<>();
      map.put("cypher", "MATCH (n:Category)--(m:Category) RETURN n,m LIMIT 4");

      HTTP.Response response = HTTP.withHeaders("Accept", "text/plain").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypher", map);

      String expected =
          "<neo4j://individuals#3> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <neo4j://vocabulary#Category> .\n"
              + "<neo4j://individuals#3> <neo4j://vocabulary#catName> \"Critic\" .\n"
              + "<neo4j://individuals#0> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <neo4j://vocabulary#Category> .\n"
              + "<neo4j://individuals#0> <neo4j://vocabulary#catName> \"Person\" .\n"
              + "<neo4j://individuals#2> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <neo4j://vocabulary#Category> .\n"
              + "<neo4j://individuals#2> <neo4j://vocabulary#catName> \"Director\" .\n"
              + "<neo4j://individuals#1> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <neo4j://vocabulary#Category> .\n"
              + "<neo4j://individuals#1> <neo4j://vocabulary#catName> \"Actor\" .\n";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.NTRIPLES, response.rawContent(), RDFFormat.NTRIPLES));

      map.put("mappedElemsOnly", "true");
      response = HTTP.withHeaders("Accept", "text/plain").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypher", map);

      assertEquals(200, response.status());
      assertEquals("", response.rawContent());


    }
  }

  @Test
  public void testCypherOnLPGMappingsAndQueryParams() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class).withProcedure(MappingUtils.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {

            String dataInsertion = "CREATE (Keanu:Actor {name:'Keanu Reeves', born:1964})\n" +
                "CREATE (Carrie:Director {name:'Carrie-Anne Moss', born:1967})\n" +
                "CREATE (Laurence:Director {name:'Laurence Fishburne', born:1961})\n" +
                "CREATE (Hugo:Critic {name:'Hugo Weaving', born:1960})\n" +
                "CREATE (AndyW:Actor {name:'Andy Wachowski', born:1967}) "
                + "CREATE (Keanu)-[:ACTED_IN]->(:Movie {title: 'The Matrix'})";
            graphDatabaseService.execute(dataInsertion);

            String mappingCreation =
                "CALL semantics.mapping.addSchema('http://schema.org/','sch') YIELD namespace  "
                    + "CALL semantics.mapping.addMappingToSchema('http://schema.org/','Actor','Person') YIELD elemName AS en1  "
                    + "CALL semantics.mapping.addMappingToSchema('http://schema.org/','born','dob') YIELD elemName AS en2 "
                    + "CALL semantics.mapping.addMappingToSchema('http://schema.org/','name','familyName') YIELD elemName AS en3 "
                    + "CALL semantics.mapping.addMappingToSchema('http://schema.org/','ACTED_IN','inMovie') YIELD elemName AS en4 "
                    + "RETURN 'OK'";
            graphDatabaseService.execute(mappingCreation);
            tx.success();
          }
          return null;
        })
        .newServer()) {

      Result result = server.graph().execute(" MATCH (n:Actor) RETURN id(n) AS id ");
      assertEquals(2, count(result));

      result = server.graph().execute(" MATCH (n:_MapDef) RETURN id(n) AS id ");
      assertEquals(4, count(result));

      Map<String, Object> map = new HashMap<>();
      map.put("cypher", "MATCH (n:Actor { name : $actorName })-[r]-(m) RETURN n, r, m ");
      Map<String, Object> cypherParams = new HashMap<String, Object>();
      cypherParams.put("actorName", "Keanu Reeves");
      map.put("cypherParams", cypherParams);

      HTTP.Response response = HTTP.withHeaders("Accept", "text/plain").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypher", map);

      String expected =
          "<neo4j://individuals#5> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <neo4j://vocabulary#Movie> .\n"
              + "<neo4j://individuals#5> <neo4j://vocabulary#title> \"The Matrix\" .\n"
              + "<neo4j://individuals#0> <http://schema.org/inMovie> <neo4j://individuals#5> .\n"
              + "<neo4j://individuals#0> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://schema.org/Person> .\n"
              + "<neo4j://individuals#0> <http://schema.org/dob> \"1964\"^^<http://www.w3.org/2001/XMLSchema#long> .\n"
              + "<neo4j://individuals#0> <http://schema.org/familyName> \"Keanu Reeves\" .\n";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.NTRIPLES, response.rawContent(), RDFFormat.NTRIPLES));

      map.put("mappedElemsOnly", "true");
      response = HTTP.withHeaders("Accept", "text/plain").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypher", map);

      String expectedOnlyMapped =
          "<neo4j://individuals#0> <http://schema.org/inMovie> <neo4j://individuals#5> .\n"
              + "<neo4j://individuals#0> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://schema.org/Person> .\n"
              + "<neo4j://individuals#0> <http://schema.org/dob> \"1964\"^^<http://www.w3.org/2001/XMLSchema#long> .\n"
              + "<neo4j://individuals#0> <http://schema.org/familyName> \"Keanu Reeves\" .\n";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expectedOnlyMapped, RDFFormat.NTRIPLES, response.rawContent(),
              RDFFormat.NTRIPLES));


    }
  }

  @Test
  public void testontoOnLPG() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String dataInsertion =
                "CREATE (kean:Actor:Resource {name:'Keanu Reeves', born:1964})\n" +
                    "CREATE (mtrx:Movie:Resource {title:'The Matrix', released:2001})\n" +
                    "CREATE (dir:Director:Resource {name:'Laurence Fishburne', born:1961})\n" +
                    "CREATE (cri:Critic:Resource {name:'Hugo Weaving', born:1960})\n" +
                    "CREATE (kean)-[:ACTED_IN]->(mtrx)\n" +
                    "CREATE (dir)-[:DIRECTED]->(mtrx)\n" +
                    "CREATE (cri)-[:RATED]->(mtrx)\n" +
                    "RETURN *";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {

      Result result = server.graph().execute("MATCH (n:Critic) RETURN id(n) AS id ");
      assertEquals(1, count(result));

      HTTP.Response response = HTTP.withHeaders("Accept", "text/plain").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "onto");

      String expected =
          "<neo4j://vocabulary#Movie> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Class> .\n"
              + "<neo4j://vocabulary#Movie> <http://www.w3.org/2000/01/rdf-schema#label> \"Movie\" .\n"
              + "<neo4j://vocabulary#Actor> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Class> .\n"
              + "<neo4j://vocabulary#Actor> <http://www.w3.org/2000/01/rdf-schema#label> \"Actor\" .\n"
              + "<neo4j://vocabulary#Director> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Class> .\n"
              + "<neo4j://vocabulary#Director> <http://www.w3.org/2000/01/rdf-schema#label> \"Director\" .\n"
              + "<neo4j://vocabulary#Critic> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Class> .\n"
              + "<neo4j://vocabulary#Critic> <http://www.w3.org/2000/01/rdf-schema#label> \"Critic\" .\n"
              + "<neo4j://vocabulary#ACTED_IN> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#ObjectProperty> .\n"
              + "<neo4j://vocabulary#ACTED_IN> <http://www.w3.org/2000/01/rdf-schema#label> \"ACTED_IN\" .\n"
              + "<neo4j://vocabulary#ACTED_IN> <http://www.w3.org/2000/01/rdf-schema#domain> <neo4j://vocabulary#Actor> .\n"
              + "<neo4j://vocabulary#ACTED_IN> <http://www.w3.org/2000/01/rdf-schema#range> <neo4j://vocabulary#Movie> .\n"
              + "<neo4j://vocabulary#RATED> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#ObjectProperty> .\n"
              + "<neo4j://vocabulary#RATED> <http://www.w3.org/2000/01/rdf-schema#label> \"RATED\" .\n"
              + "<neo4j://vocabulary#RATED> <http://www.w3.org/2000/01/rdf-schema#domain> <neo4j://vocabulary#Critic> .\n"
              + "<neo4j://vocabulary#RATED> <http://www.w3.org/2000/01/rdf-schema#range> <neo4j://vocabulary#Movie> .\n"
              + "<neo4j://vocabulary#DIRECTED> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#ObjectProperty> .\n"
              + "<neo4j://vocabulary#DIRECTED> <http://www.w3.org/2000/01/rdf-schema#label> \"DIRECTED\" .\n"
              + "<neo4j://vocabulary#DIRECTED> <http://www.w3.org/2000/01/rdf-schema#domain> <neo4j://vocabulary#Director> .\n"
              + "<neo4j://vocabulary#DIRECTED> <http://www.w3.org/2000/01/rdf-schema#range> <neo4j://vocabulary#Movie> .\n";
      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.NTRIPLES, response.rawContent(), RDFFormat.NTRIPLES));


    }
  }

  @Test
  public void testontoOnRDF() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String nsDefCreation =
                "CREATE (n:NamespacePrefixDefinition { `http://ont.thomsonreuters.com/mdaas/` : 'ns1' ,\n"
                    +
                    "`http://permid.org/ontology/organization/` : 'ns0' } ) ";
            graphDatabaseService.execute(nsDefCreation);
            String dataInsertion =
                "CREATE (Keanu:Resource:ns0" + PREFIX_SEPARATOR + "Actor {ns1" + PREFIX_SEPARATOR
                    + "name:'Keanu Reeves', ns1" + PREFIX_SEPARATOR
                    + "born:1964, uri: 'https://permid.org/1-21523433750' })\n" +
                    "CREATE (Carrie:Resource:ns0" + PREFIX_SEPARATOR + "Director {ns1"
                    + PREFIX_SEPARATOR + "name:'Carrie-Anne Moss', ns1" + PREFIX_SEPARATOR
                    + "born:1967, uri: 'https://permid.org/1-21523433751' })\n" +
                    "CREATE (Laurence:Resource:ns0" + PREFIX_SEPARATOR + "Director {ns1"
                    + PREFIX_SEPARATOR + "name:'Laurence Fishburne', ns1" + PREFIX_SEPARATOR
                    + "born:1961, uri: 'https://permid.org/1-21523433752' })\n" +
                    "CREATE (Hugo:Resource:ns0" + PREFIX_SEPARATOR + "Critic {ns1"
                    + PREFIX_SEPARATOR + "name:'Hugo Weaving', ns1" + PREFIX_SEPARATOR
                    + "born:1960, uri: 'https://permid.org/1-21523433753' })\n" +
                    "CREATE (AndyW:Resource:ns0" + PREFIX_SEPARATOR + "Actor {ns1"
                    + PREFIX_SEPARATOR + "name:'Andy Wachowski', ns1" + PREFIX_SEPARATOR
                    + "born:1967, uri: 'https://permid.org/1-21523433754' })\n" +
                    "CREATE (Keanu)-[:ns0" + PREFIX_SEPARATOR + "Likes]->(Carrie) \n" +
                    "CREATE (Keanu)<-[:ns0" + PREFIX_SEPARATOR + "FriendOf]-(Hugo) ";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "text/plain").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "ontonrdf");

      String expected =
          "<http://permid.org/ontology/organization/Director> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Class> .\n"
              +
              "<http://permid.org/ontology/organization/Director> <http://www.w3.org/2000/01/rdf-schema#label> \"Director\" .\n"
              +
              "<http://permid.org/ontology/organization/Actor> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Class> .\n"
              +
              "<http://permid.org/ontology/organization/Actor> <http://www.w3.org/2000/01/rdf-schema#label> \"Actor\" .\n"
              +
              "<http://permid.org/ontology/organization/Critic> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Class> .\n"
              +
              "<http://permid.org/ontology/organization/Critic> <http://www.w3.org/2000/01/rdf-schema#label> \"Critic\" .\n"
              +
              "<http://permid.org/ontology/organization/Likes> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#ObjectProperty> .\n"
              +
              "<http://permid.org/ontology/organization/Likes> <http://www.w3.org/2000/01/rdf-schema#label> \"Likes\" .\n"
              +
              "<http://permid.org/ontology/organization/Likes> <http://www.w3.org/2000/01/rdf-schema#range> <http://permid.org/ontology/organization/Director> .\n"
              +
              "<http://permid.org/ontology/organization/Likes> <http://www.w3.org/2000/01/rdf-schema#domain> <http://permid.org/ontology/organization/Actor> .\n"
              +
              "<http://permid.org/ontology/organization/FriendOf> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#ObjectProperty> .\n"
              +
              "<http://permid.org/ontology/organization/FriendOf> <http://www.w3.org/2000/01/rdf-schema#label> \"FriendOf\" .\n"
              +
              "<http://permid.org/ontology/organization/FriendOf> <http://www.w3.org/2000/01/rdf-schema#domain> <http://permid.org/ontology/organization/Critic> .\n"
              +
              "<http://permid.org/ontology/organization/FriendOf> <http://www.w3.org/2000/01/rdf-schema#range> <http://permid.org/ontology/organization/Actor> .";
      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.NTRIPLES, response.rawContent(), RDFFormat.NTRIPLES));


    }
  }

  @Test
  public void testNodeByUri() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String nsDefCreation =
                "CREATE (n:NamespacePrefixDefinition { `http://ont.thomsonreuters.com/mdaas/` : 'ns1' ,\n"
                    +
                    "`http://permid.org/ontology/organization/` : 'ns0' } ) ";
            graphDatabaseService.execute(nsDefCreation);
            String dataInsertion =
                "CREATE (Keanu:Resource:ns0" + PREFIX_SEPARATOR + "Actor {ns1" + PREFIX_SEPARATOR
                    + "name:'Keanu Reeves', ns1" + PREFIX_SEPARATOR
                    + "born:1964, uri: 'https://permid.org/1-21523433750' })\n" +
                    "CREATE (Carrie:Resource:ns0" + PREFIX_SEPARATOR + "Director {ns1"
                    + PREFIX_SEPARATOR + "name:'Carrie-Anne Moss', ns1" + PREFIX_SEPARATOR
                    + "born:1967, uri: 'https://permid.org/1-21523433751' })\n" +
                    "CREATE (Laurence:Resource:ns0" + PREFIX_SEPARATOR + "Director {ns1"
                    + PREFIX_SEPARATOR + "name:'Laurence Fishburne', ns1" + PREFIX_SEPARATOR
                    + "born:1961, uri: 'https://permid.org/1-21523433752' })\n" +
                    "CREATE (Hugo:Resource:ns0" + PREFIX_SEPARATOR + "Critic {ns1"
                    + PREFIX_SEPARATOR + "name:'Hugo Weaving', ns1" + PREFIX_SEPARATOR
                    + "born:1960, uri: 'https://permid.org/1-21523433753' })\n" +
                    "CREATE (AndyW:Resource:ns0" + PREFIX_SEPARATOR + "Actor {ns1"
                    + PREFIX_SEPARATOR + "name:'Andy Wachowski', ns1" + PREFIX_SEPARATOR
                    + "born:1967, uri: 'https://permid.org/1-21523433754' })\n" +
                    "CREATE (Keanu)-[:ns0" + PREFIX_SEPARATOR + "Likes]->(Carrie) \n" +
                    "CREATE (Keanu)<-[:ns0" + PREFIX_SEPARATOR + "FriendOf]-(Hugo) ";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {

      Result result = server.graph()
          .execute("MATCH (n:ns0" + PREFIX_SEPARATOR + "Critic) RETURN id(n) AS id ");
      //assertEquals( 1, count( result ) );

      Long id = (Long) result.next().get("id");

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder
              .encode("https://permid.org/1-21523433750", StandardCharsets.UTF_8.toString()));
      //TODO Make it better
      String expected = "@prefix neovoc: <neo4j://vocabulary#> .\n" +
          "\n" +
          "\n" +
          "<https://permid.org/1-21523433750> a <http://permid.org/ontology/organization/Actor>;\n"
          +
          "  <http://ont.thomsonreuters.com/mdaas/born> \"1964\"^^<http://www.w3.org/2001/XMLSchema#long>;\n"
          +
          "  <http://ont.thomsonreuters.com/mdaas/name> \"Keanu Reeves\";\n" +
          "  <http://permid.org/ontology/organization/Likes> <https://permid.org/1-21523433751> .\n"
          +
          "\n" +
          "<https://permid.org/1-21523433753> <http://permid.org/ontology/organization/FriendOf>\n"
          +
          "    <https://permid.org/1-21523433750> .\n";
      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));

    }
  }

  @Test
  public void testNodeByUriAfterImport() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFEndpointTest.class.getClassLoader().getResource("fibo-fragment.rdf")
                    .toURI() + "','RDF/XML',{})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "application/rdf+xml").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder.encode(
              "https://spec.edmcouncil.org/fibo/ontology/BE/Corporations/Corporations/BoardAgreement",
              StandardCharsets.UTF_8.toString())
              + "?excludeContext=true");

      String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
          "<rdf:RDF\txmlns:neovoc=\"neo4j://vocabulary#\"" +
          "\txmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
          "<rdf:Description rdf:about=\"https://spec.edmcouncil.org/fibo/ontology/BE/Corporations/Corporations/BoardAgreement\">"
          +
          "\t<rdf:type rdf:resource=\"http://www.w3.org/2002/07/owl#Class\"/>" +
          "\t<definition xmlns=\"http://www.w3.org/2004/02/skos/core#\">a formal, legally binding agreement between members of the Board of Directors of the organization</definition>"
          +
          "\t<label xmlns=\"http://www.w3.org/2000/01/rdf-schema#\">board agreement</label>" +
          "</rdf:Description></rdf:RDF>";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.RDFXML, response.rawContent(), RDFFormat.RDFXML));

      //uris need to be urlencoded. Normally not a problem but beware of hash signs!!
      response = HTTP.withHeaders("Accept", "text/plain").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "describe/uri/"
              + URLEncoder.encode("http://www.w3.org/2004/02/skos/core#TestyMcTestFace", "UTF-8")
      );

      expected = "<https://spec.edmcouncil.org/fibo/ontology/BE/Corporations/Corporations/> <http://www.omg.org/techprocess/ab/SpecificationMetadata/linkToResourceAddedForTestingPurposesByJB> <http://www.w3.org/2004/02/skos/core#TestyMcTestFace> .";
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.NTRIPLES, response.rawContent(), RDFFormat.NTRIPLES));
      assertEquals(200, response.status());
    }
  }


  @Test
  public void testNodeByUriMissingNamespaceDefinition() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");
            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            //set a prefix that we can remove afterwards
            graphDatabaseService.execute("CREATE (n:NamespacePrefixDefinition) "
                + "SET n.`https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/AnnotationVocabulary/` = 'fiboanno'");

            graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFEndpointTest.class.getClassLoader().getResource("fibo-fragment.rdf")
                    .toURI() + "','RDF/XML',{})");

            //we remove the namespace now
            graphDatabaseService.execute("MATCH (n:NamespacePrefixDefinition) "
                + "REMOVE n.`https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/AnnotationVocabulary/`");
            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "application/rdf+xml").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder
              .encode("https://spec.edmcouncil.org/fibo/ontology/BE/Corporations/Corporations/",
                  StandardCharsets.UTF_8.toString()));
      assertEquals(200, response.status());
      String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
          + "<rdf:RDF\n"
          + "\txmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
          + "<!-- RDF Serialization ERROR: Prefix fiboanno in use but not defined in the 'NamespacePrefixDefinition' node -->\n"
          + "\n"
          + "</rdf:RDF>";
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.RDFXML, response.rawContent(), RDFFormat.RDFXML));
      assertTrue(response.rawContent().contains("RDF Serialization ERROR: Prefix fiboanno in use "
          + "but not defined in the 'NamespacePrefixDefinition' node"));
    }
  }

  @Test
  public void testNodeByUriAfterImportWithMultilang() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFEndpointTest.class.getClassLoader().getResource("multilang.ttl")
                    .toURI() + "','Turtle',{ keepLangTag : true, handleMultival: 'ARRAY'})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder
              .encode("http://example.org/vocab/show/218", StandardCharsets.UTF_8.toString()));

      String expected = "@prefix show: <http://example.org/vocab/show/> .\n" +
          "show:218 show:localName \"That Seventies Show\"@en .                 # literal with a language tag\n"
          +
          "show:218 show:localName 'Cette Série des Années Soixante-dix'@fr . # literal delimited by single quote\n"
          +
          "show:218 show:localName \"Cette Série des Années Septante\"@fr-be .  # literal with a region subtag";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));


    }
  }


  @Test
  public void testCypherWithUrisSerializeAsJsonLd() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String nsDefCreation =
                "CREATE (n:NamespacePrefixDefinition { `http://ont.thomsonreuters.com/mdaas/` : 'ns1' ,\n"
                    +
                    "`http://permid.org/ontology/organization/` : 'ns0' } ) ";
            graphDatabaseService.execute(nsDefCreation);
            String dataInsertion =
                "CREATE (Keanu:Resource:ns0" + PREFIX_SEPARATOR + "Actor {ns1" + PREFIX_SEPARATOR
                    + "name:'Keanu Reeves', ns1" + PREFIX_SEPARATOR
                    + "born:1964, uri: 'https://permid.org/1-21523433750' })\n" +
                    "CREATE (Carrie:Resource:ns0" + PREFIX_SEPARATOR + "Director {ns1"
                    + PREFIX_SEPARATOR + "name:'Carrie-Anne Moss', ns1" + PREFIX_SEPARATOR
                    + "born:1967, uri: 'https://permid.org/1-21523433751' })\n" +
                    "CREATE (Laurence:Resource:ns0" + PREFIX_SEPARATOR + "Director {ns1"
                    + PREFIX_SEPARATOR + "name:'Laurence Fishburne', ns1" + PREFIX_SEPARATOR
                    + "born:1961, uri: 'https://permid.org/1-21523433752' })\n" +
                    "CREATE (Hugo:Resource:ns0" + PREFIX_SEPARATOR + "Critic {ns1"
                    + PREFIX_SEPARATOR + "name:'Hugo Weaving', ns1" + PREFIX_SEPARATOR
                    + "born:1960, uri: 'https://permid.org/1-21523433753' })\n" +
                    "CREATE (AndyW:Resource:ns0" + PREFIX_SEPARATOR + "Actor {ns1"
                    + PREFIX_SEPARATOR + "name:'Andy Wachowski', ns1" + PREFIX_SEPARATOR
                    + "born:1967, uri: 'https://permid.org/1-21523433754' })\n" +
                    "CREATE (Keanu)-[:ns0" + PREFIX_SEPARATOR + "Likes]->(Carrie) ";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {

      Result result = server.graph()
          .execute("MATCH (n:ns0" + PREFIX_SEPARATOR + "Critic) RETURN id(n) AS id ");
      //assertEquals( 1, count( result ) );

      Long id = (Long) result.next().get("id");

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (n:Resource) RETURN n LIMIT 1");

      HTTP.Response response = HTTP.withHeaders("Accept", "application/ld+json").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf", params);

      String expected = "[ {\n" +
          "  \"@id\" : \"https://permid.org/1-21523433750\",\n" +
          "  \"@type\" : [ \"http://permid.org/ontology/organization/Actor\" ],\n" +
          "  \"http://ont.thomsonreuters.com/mdaas/born\" : [ {\n" +
          "    \"@type\" : \"http://www.w3.org/2001/XMLSchema#long\",\n" +
          "    \"@value\" : \"1964\"\n" +
          "  } ],\n" +
          "  \"http://ont.thomsonreuters.com/mdaas/name\" : [ {\n" +
          "    \"@value\" : \"Keanu Reeves\"\n" +
          "  } ]\n" +
          "} ]";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.JSONLD, response.rawContent(), RDFFormat.JSONLD));
    }
  }

  @Test
  public void testOneNodeCypherWithUrisSerializeAsJsonLd() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String nsDefCreation =
                "CREATE (n:NamespacePrefixDefinition { `http://ont.thomsonreuters.com/mdaas/` : 'ns1' ,\n"
                    +
                    "`http://permid.org/ontology/organization/` : 'ns0' } ) ";
            graphDatabaseService.execute(nsDefCreation);
            String dataInsertion =
                "CREATE (Keanu:Resource:ns0" + PREFIX_SEPARATOR + "Actor {ns1" + PREFIX_SEPARATOR
                    + "name:'Keanu Reeves', ns1" + PREFIX_SEPARATOR
                    + "born:1964, uri: 'https://permid.org/1-21523433750' }) ";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {

      Result result = server.graph()
          .execute("MATCH (n) RETURN id(n) AS id ");
      //assertEquals( 1, count( result ) );

      Long id = (Long) result.next().get("id");

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (n) RETURN n ");

      HTTP.Response response = HTTP.withHeaders("Accept", "application/ld+json").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf", params);

      String expected = "[ {\n" +
          "  \"@id\" : \"https://permid.org/1-21523433750\",\n" +
          "  \"@type\" : [ \"http://permid.org/ontology/organization/Actor\" ],\n" +
          "  \"http://ont.thomsonreuters.com/mdaas/born\" : [ {\n" +
          "    \"@type\" : \"http://www.w3.org/2001/XMLSchema#long\",\n" +
          "    \"@value\" : \"1964\"\n" +
          "  } ],\n" +
          "  \"http://ont.thomsonreuters.com/mdaas/name\" : [ {\n" +
          "    \"@value\" : \"Keanu Reeves\"\n" +
          "  } ]\n" +
          "} ]";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.JSONLD, response.rawContent(), RDFFormat.JSONLD));
    }
  }

  @Test
  public void testCypherWithBNodesSerializeAsRDFXML() throws Exception {
    try (ServerControls server = getServerBuilder()
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            String nsDefCreation =
                "CREATE (n:NamespacePrefixDefinition { `http://ont.thomsonreuters.com/mdaas/` : 'ns1' ,\n"
                    +
                    "`http://permid.org/ontology/organization/` : 'ns0' } ) ";
            graphDatabaseService.execute(nsDefCreation);
            String dataInsertion =
                "CREATE (Keanu:Resource:ns0" + PREFIX_SEPARATOR + "Actor {ns1" + PREFIX_SEPARATOR
                    + "name:'Keanu Reeves', ns1" + PREFIX_SEPARATOR
                    + "born:1964, uri: '_:1-21523433750' })\n" +
                    "CREATE (Carrie:Resource:ns0" + PREFIX_SEPARATOR + "Director {ns1"
                    + PREFIX_SEPARATOR + "name:'Carrie-Anne Moss', ns1" + PREFIX_SEPARATOR
                    + "born:1967, uri: 'https://permid.org/1-21523433751' })\n" +
                    "CREATE (Laurence:Resource:ns0" + PREFIX_SEPARATOR + "Director {ns1"
                    + PREFIX_SEPARATOR + "name:'Laurence Fishburne', ns1" + PREFIX_SEPARATOR
                    + "born:1961, uri: 'https://permid.org/1-21523433752' })\n" +
                    "CREATE (Hugo:Resource:ns0" + PREFIX_SEPARATOR + "Critic {ns1"
                    + PREFIX_SEPARATOR + "name:'Hugo Weaving', ns1" + PREFIX_SEPARATOR
                    + "born:1960, uri: 'https://permid.org/1-21523433753' })\n" +
                    "CREATE (AndyW:Resource:ns0" + PREFIX_SEPARATOR + "Actor {ns1"
                    + PREFIX_SEPARATOR + "name:'Andy Wachowski', ns1" + PREFIX_SEPARATOR
                    + "born:1967, uri: 'https://permid.org/1-21523433754' })\n" +
                    "CREATE (Keanu)-[:ns0" + PREFIX_SEPARATOR + "Likes]->(Carrie) ";
            graphDatabaseService.execute(dataInsertion);
            tx.success();
          }
          return null;
        })
        .newServer()) {

      ValueFactory factory = SimpleValueFactory.getInstance();

      Result result = server.graph()
          .execute("MATCH (n:ns0" + PREFIX_SEPARATOR + "Critic) RETURN id(n) AS id ");

      Long id = (Long) result.next().get("id");

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (a)-[r:ns0" + PREFIX_SEPARATOR + "Likes]-(b) RETURN *");

      HTTP.Response response = HTTP.withHeaders("Accept", "application/rdf+xml").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf", params);

      String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
          "<rdf:RDF\n" +
          "\txmlns:neovoc=\"neo4j://vocabulary#\"\n" +
          "\txmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
          "\n" +
          "<rdf:Description rdf:about=\"https://permid.org/1-21523433751\">\n" +
          "\t<rdf:type rdf:resource=\"http://permid.org/ontology/organization/Director\"/>\n" +
          "\t<born xmlns=\"http://ont.thomsonreuters.com/mdaas/\" rdf:datatype=\"http://www.w3.org/2001/XMLSchema#long\">1967</born>\n"
          +
          "\t<name xmlns=\"http://ont.thomsonreuters.com/mdaas/\">Carrie-Anne Moss</name>\n" +
          "</rdf:Description>\n" +
          "\n" +
          "<rdf:Description rdf:about=\"_:1-21523433750\">\n" +
          "\t<Likes xmlns=\"http://permid.org/ontology/organization/\" rdf:resource=\"https://permid.org/1-21523433751\"/>\n"
          +
          "\t<rdf:type rdf:resource=\"http://permid.org/ontology/organization/Actor\"/>\n" +
          "\t<born xmlns=\"http://ont.thomsonreuters.com/mdaas/\" rdf:datatype=\"http://www.w3.org/2001/XMLSchema#long\">1964</born>\n"
          +
          "\t<name xmlns=\"http://ont.thomsonreuters.com/mdaas/\">Keanu Reeves</name>\n" +
          "\t<Likes xmlns=\"http://permid.org/ontology/organization/\" rdf:resource=\"https://permid.org/1-21523433751\"/>\n"
          +
          "</rdf:Description>\n" +
          "\n" +
          "</rdf:RDF>";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.RDFXML, response.rawContent(), RDFFormat.RDFXML));

    }
  }

  @Test
  public void testNodeByUriAfterImportWithCustomDTKeepUris() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFImportTest.class.getClassLoader().getResource("customDataTypes2.ttl")
                    .toURI()
                + "','Turtle',{keepLangTag: true, handleVocabUris: 'KEEP', handleMultival: 'OVERWRITE', "
                +
                "keepCustomDataTypes: true, typesToLabels: true})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder
              .encode("http://example.org/Resource1", StandardCharsets.UTF_8.toString()));

      String expected = "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
          "\n" +
          "<http://example.org/Resource1>\n" +
          "                                a  <http://example.org/Resource>;\n" +
          "  <http://example.org/Predicate1>  \"2008-04-17\"^^<http://www.w3.org/2001/XMLSchema#date>;\n"
          +
          "  <http://example.org/Predicate2>  \"4.75\"^^xsd:double;\n" +
          "  <http://example.org/Predicate3>  \"2\"^^xsd:long;\n" +
          "  <http://example.org/Predicate4>  true;\n" +
          "  <http://example.org/Predicate5>  \"2\"^^xsd:double;\n" +
          "  <http://example.org/Predicate6>  \"4\"^^xsd:double;\n" +
          "  <http://example.org/Predicate7>  \"52.63\"^^<http://example.org/USD>;\n"
          +
          "  <http://example.org/Predicate8>  \"2008-03-22T00:00:00\"^^<http://www.w3.org/2001/XMLSchema#dateTime>;\n"
          +
          "  <http://example.org/Predicate9> \"-100\"^^xsd:long.";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));

    }
  }

  @Test
  public void testNodeByUriAfterImportWithCustomDTShortenURIs() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFImportTest.class.getClassLoader().getResource("customDataTypes2.ttl")
                    .toURI()
                + "','Turtle',{keepLangTag: true, handleVocabUris: 'SHORTEN', handleMultival: 'OVERWRITE', "
                +
                "keepCustomDataTypes: true, typesToLabels: true})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder
              .encode("http://example.org/Resource1", StandardCharsets.UTF_8.toString()));

      String expected = "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
          "\n" +
          "<http://example.org/Resource1>\n" +
          "                                a  <http://example.org/Resource>;\n" +
          "  <http://example.org/Predicate1>  \"2008-04-17\"^^<http://www.w3.org/2001/XMLSchema#date>;\n"
          +
          "  <http://example.org/Predicate2>  \"4.75\"^^xsd:double;\n" +
          "  <http://example.org/Predicate3>  \"2\"^^xsd:long;\n" +
          "  <http://example.org/Predicate4>  true;\n" +
          "  <http://example.org/Predicate5>  \"2\"^^xsd:double;\n" +
          "  <http://example.org/Predicate6>  \"4\"^^xsd:double;\n" +
          "  <http://example.org/Predicate7>  \"52.63\"^^<http://example.org/USD>;\n"
          +
          "  <http://example.org/Predicate8>  \"2008-03-22T00:00:00\"^^<http://www.w3.org/2001/XMLSchema#dateTime>;\n"
          +
          "  <http://example.org/Predicate9> \"-100\"^^xsd:long.";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));
    }
  }

  @Test
  public void testNodeByUriAfterImportWithMultiCustomDTKeepUris() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFImportTest.class.getClassLoader().getResource("customDataTypes.ttl")
                    .toURI()
                + "','Turtle',{keepLangTag: true, handleVocabUris: 'KEEP', handleMultival: 'ARRAY', "
                +
                "multivalPropList: ['http://example.com/price', 'http://example.com/power'], keepCustomDataTypes: true, "
                +
                "customDataTypedPropList: ['http://example.com/price', 'http://example.com/color']})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder
              .encode("http://example.com/Mercedes", StandardCharsets.UTF_8.toString()));

      String expected = "@prefix ex: <http://example.com/> .\n" +
          "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
          "\n" +
          "ex:Mercedes \n" +
          "\trdf:type ex:Car ;\n" +
          "\tex:price \"10000\"^^ex:EUR ;\n" +
          "\tex:price \"11000\"^^ex:USD ;\n" +
          "\tex:power \"300\" ;\n" +
          "\tex:power \"223,71\" ;\n" +
          "\tex:color \"red\"^^ex:Color ;\n" +
          "\tex:class \"A-Class\"@en ;\n" +
          "\tex:released \"2019\"^^xsd:long ;\n" +
          "\tex:type \"Cabrio\" .";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));

    }
  }

  @Test
  public void testNodeByUriAfterImportWithMultiCustomDTShortenUris() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFImportTest.class.getClassLoader().getResource("customDataTypes.ttl")
                    .toURI()
                + "','Turtle',{keepLangTag: true, handleVocabUris: 'SHORTEN', handleMultival: 'ARRAY', "
                +
                "multivalPropList: ['http://example.com/price', 'http://example.com/power'], keepCustomDataTypes: true, "
                +
                "customDataTypedPropList: ['http://example.com/price', 'http://example.com/color']})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder
              .encode("http://example.com/Mercedes", StandardCharsets.UTF_8.toString()));

      String expected = "@prefix ex: <http://example.com/> .\n" +
          "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
          "\n" +
          "ex:Mercedes \n" +
          "\trdf:type ex:Car ;\n" +
          "\tex:price \"10000\"^^ex:EUR ;\n" +
          "\tex:price \"11000\"^^ex:USD ;\n" +
          "\tex:power \"300\" ;\n" +
          "\tex:power \"223,71\" ;\n" +
          "\tex:color \"red\"^^ex:Color ;\n" +
          "\tex:class \"A-Class\"@en ;\n" +
          "\tex:released \"2019\"^^xsd:long ;\n" +
          "\tex:type \"Cabrio\" .";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));

    }
  }

  @Test
  public void testCypherOnRDFAfterImportWithCustomDTKeepURIsSerializeAsTurtle() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFImportTest.class.getClassLoader().getResource("customDataTypes2.ttl")
                    .toURI()
                + "','Turtle',{keepLangTag: true, handleVocabUris: 'KEEP', handleMultival: 'OVERWRITE', "
                +
                "keepCustomDataTypes: true})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (n {uri: 'http://example.org/Resource1'})" +
          "OPTIONAL MATCH (n)-[]-(m) RETURN *");

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf", params);

      String expected = "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
          "\n" +
          "<http://example.org/Resource1>\n" +
          "                                a  <http://example.org/Resource>;\n" +
          "  <http://example.org/Predicate1>  \"2008-04-17\"^^<http://www.w3.org/2001/XMLSchema#date>;\n"
          +
          "  <http://example.org/Predicate2>  \"4.75\"^^xsd:double;\n" +
          "  <http://example.org/Predicate3>  \"2\"^^xsd:long;\n" +
          "  <http://example.org/Predicate4>  true;\n" +
          "  <http://example.org/Predicate5>  \"2\"^^xsd:double;\n" +
          "  <http://example.org/Predicate6>  \"4\"^^xsd:double;\n" +
          "  <http://example.org/Predicate7>  \"52.63\"^^<http://example.org/USD>;\n"
          +
          "  <http://example.org/Predicate8>  \"2008-03-22T00:00:00\"^^<http://www.w3.org/2001/XMLSchema#dateTime>;\n"
          +
          "  <http://example.org/Predicate9> \"-100\"^^xsd:long.";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));

    }
  }

  @Test
  public void testCypherOnRDFDatesAndDatetimes() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFImportTest.class.getClassLoader()
                    .getResource("datetime/datetime-simple-multivalued.ttl")
                    .toURI()
                + "','Turtle',{handleMultival: 'ARRAY'})");

            tx.success();
          } catch (Exception e) {
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (n) RETURN *");

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf", params);

      String expected = "@prefix rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#>.\n"
          + "@prefix xsd:     <http://www.w3.org/2001/XMLSchema#>.\n"
          + "@prefix exterms: <hhttp://www.example.org/terms/>.\n"
          + "@prefix ex: <hhttp://www.example.org/indiv/>.\n"
          + "\n"
          + "ex:index.html  exterms:someDateValue  \"1999-08-16\"^^xsd:date, \"1999-08-17\"^^xsd:date, \"1999-08-18\"^^xsd:date  ;\n"
          + "               exterms:someDateTimeValues \"2012-12-31T23:57:00\"^^xsd:dateTime, \"2012-12-30T23:57:00\"^^xsd:dateTime .";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));

    }
  }


  @Test
  public void testCypherOnRDFErrorWhereModelIsNotRDF() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute(""
                + "CREATE (:Node { uri: 'neo4j://ind#123' })-[:LINKED_TO]->(:Node { id: 124 })");
            tx.success();
          } catch (Exception e) {
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (n)-[r]-(m) RETURN *");

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf", params);

      assertEquals(200, response.status());
      assertEquals("# No such property, 'uri'.\n", response.rawContent());

    }
  }


  @Test
  public void testCypherOnRDFAfterImportWithCustomDTShortenURIsSerializeAsTurtle()
      throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFImportTest.class.getClassLoader().getResource("customDataTypes2.ttl")
                    .toURI()
                + "','Turtle',{keepLangTag: true, handleVocabUris: 'SHORTEN', handleMultival: 'OVERWRITE', "
                +
                "keepCustomDataTypes: true})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (n {uri: 'http://example.org/Resource1'})" +
          "OPTIONAL MATCH (n)-[]-(m) RETURN *");

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf", params);

      String expected = "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
          "\n" +
          "<http://example.org/Resource1>\n" +
          "                                a  <http://example.org/Resource>;\n" +
          "  <http://example.org/Predicate1>  \"2008-04-17\"^^<http://www.w3.org/2001/XMLSchema#date>;\n"
          +
          "  <http://example.org/Predicate2>  \"4.75\"^^xsd:double;\n" +
          "  <http://example.org/Predicate3>  \"2\"^^xsd:long;\n" +
          "  <http://example.org/Predicate4>  true;\n" +
          "  <http://example.org/Predicate5>  \"2\"^^xsd:double;\n" +
          "  <http://example.org/Predicate6>  \"4\"^^xsd:double;\n" +
          "  <http://example.org/Predicate7>  \"52.63\"^^<http://example.org/USD>;\n"
          +
          "  <http://example.org/Predicate8>  \"2008-03-22T00:00:00\"^^<http://www.w3.org/2001/XMLSchema#dateTime>;\n"
          +
          "  <http://example.org/Predicate9> \"-100\"^^xsd:long.";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));

    }
  }

  @Test
  public void testCypherOnRDFAfterImportWithMultiCustomDTKeepURIsSerializeAsTurtle()
      throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFImportTest.class.getClassLoader().getResource("customDataTypes.ttl")
                    .toURI()
                + "','Turtle',{keepLangTag: true, handleVocabUris: 'KEEP', handleMultival: 'ARRAY', "
                +
                "multivalPropList: ['http://example.com/price', 'http://example.com/power', 'http://example.com/class'], "
                +
                "keepCustomDataTypes: true, customDataTypedPropList: ['http://example.com/price', 'http://example.com/color']})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (a:`http://example.com/Car`) RETURN *");

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf", params);

      String expected = "@prefix ex: <http://example.com/> .\n" +
          "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
          "\n" +
          "ex:Mercedes \n" +
          "\trdf:type ex:Car ;\n" +
          "\tex:price \"10000\"^^ex:EUR ;\n" +
          "\tex:price \"11000\"^^ex:USD ;\n" +
          "\tex:power \"300\" ;\n" +
          "\tex:power \"223,71\" ;\n" +
          "\tex:color \"red\"^^ex:Color ;\n" +
          "\tex:class \"A-Klasse\"@de ;\n" +
          "\tex:class \"A-Class\"@en ;\n" +
          "\tex:released \"2019\"^^xsd:long ;\n" +
          "\tex:type \"Cabrio\" .";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));

    }
  }

  @Test
  public void testCypherOnRDFAfterImportWithMultiCustomDTShortenURIsSerializeAsTurtle()
      throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            Result res = graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFImportTest.class.getClassLoader().getResource("customDataTypes.ttl")
                    .toURI()
                + "','Turtle',{keepLangTag: true, handleVocabUris: 'SHORTEN', handleMultival: 'ARRAY', "
                +
                "multivalPropList: ['http://example.com/price', 'http://example.com/power', 'http://example.com/class'], "
                +
                "keepCustomDataTypes: true, customDataTypedPropList: ['http://example.com/price', 'http://example.com/color']})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (a:ns0__Car) RETURN *");

      HTTP.Response response = HTTP.withHeaders("Accept", "text/turtle").POST(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf", params);

      String expected = "@prefix ex: <http://example.com/> .\n" +
          "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
          "\n" +
          "ex:Mercedes \n" +
          "\trdf:type ex:Car ;\n" +
          "\tex:price \"10000\"^^ex:EUR ;\n" +
          "\tex:price \"11000\"^^ex:USD ;\n" +
          "\tex:power \"300\" ;\n" +
          "\tex:power \"223,71\" ;\n" +
          "\tex:color \"red\"^^ex:Color ;\n" +
          "\tex:class \"A-Klasse\"@de ;\n" +
          "\tex:class \"A-Class\"@en ;\n" +
          "\tex:released \"2019\"^^xsd:long ;\n" +
          "\tex:type \"Cabrio\" .";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));

    }
  }

  @Test
  public void testCypherOnRDFAfterDeleteRDFBNodes() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CALL semantics.importRDF('" +
                RDFImportTest.class.getClassLoader().getResource("deleteRDF/bNodes.ttl")
                    .toURI()
                + "','Turtle',{keepLangTag: true, handleVocabUris: 'KEEP', handleMultival: 'ARRAY', keepCustomDataTypes: true})");
            Result res = graphDatabaseService.execute("CALL semantics.deleteRDF('" +
                RDFImportTest.class.getClassLoader().getResource("deleteRDF/bNodesDeletion.ttl")
                    .toURI()
                + "','Turtle',{keepLangTag: true, handleVocabUris: 'KEEP', handleMultival: 'ARRAY', keepCustomDataTypes: true})");
            Map map = res.next();
            assertEquals(1L, map.get("triplesDeleted"));
            assertEquals(
                "8 of the statements could not be deleted, due to containing a blank node.",
                map.get("extraInfo"));
            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (a:Resource) "
          + "OPTIONAL MATCH (a)-[r]->()"
          + "RETURN DISTINCT *");

      HTTP.Response response = HTTP.
          withHeaders("Accept", "text/turtle")
          .POST(
              HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf",
              params);

      String expected = Resources
          .toString(Resources.getResource("deleteRDF/bNodesPostDeletion.ttl"),
              StandardCharsets.UTF_8);
      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TURTLE, response.rawContent(), RDFFormat.TURTLE));

    }
  }

  @Test
  public void testCypherOnQuadRDFSerializeAsTriG() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CALL semantics.importQuadRDF('" +
                RDFImportTest.class.getClassLoader().getResource("RDFDatasets/RDFDataset.trig")
                    .toURI()
                + "','TriG',{ handleVocabUris: 'KEEP', typesToLabels: true, commitSize: 500, keepCustomDataTypes: true, handleMultival: 'ARRAY'})");
            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (a:Resource) "
          + "OPTIONAL MATCH (a)-[r]->(b:Resource)"
          + "RETURN DISTINCT *");

      HTTP.Response response = HTTP.
          withHeaders("Accept", "application/trig")
          .POST(
              HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf",
              params);

      String expected = Resources
          .toString(Resources.getResource("RDFDatasets/RDFDataset.trig"),
              StandardCharsets.UTF_8);
      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TRIG, response.rawContent(), RDFFormat.TRIG));

    }
  }

  @Test
  public void testCypherOnQuadRDFSerializeAsNQuads() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CALL semantics.importQuadRDF('" +
                RDFImportTest.class.getClassLoader().getResource("RDFDatasets/RDFDataset.nq")
                    .toURI()
                + "','N-Quads',{ handleVocabUris: 'KEEP', typesToLabels: true, commitSize: 500, keepCustomDataTypes: true, handleMultival: 'ARRAY'})");
            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (a:Resource) "
          + "OPTIONAL MATCH (a)-[r]->(b)"
          + "RETURN DISTINCT *");

      HTTP.Response response = HTTP.
          withHeaders("Accept", "application/n-quads")
          .POST(
              HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf",
              params);

      String expected = Resources
          .toString(Resources.getResource("RDFDatasets/RDFDataset.nq"),
              StandardCharsets.UTF_8);
      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.NQUADS, response.rawContent(), RDFFormat.NQUADS));

    }
  }

  @Test
  public void testNodeByUriOnQuadRDF() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CALL semantics.importQuadRDF('" +
                RDFImportTest.class.getClassLoader().getResource("RDFDatasets/RDFDataset.trig")
                    .toURI()
                + "','TriG',{ handleVocabUris: 'KEEP', typesToLabels: true, commitSize: 500, keepCustomDataTypes: true, handleMultival: 'ARRAY'})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "application/trig").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder
              .encode("http://www.example.org/exampleDocument#Monica",
                  StandardCharsets.UTF_8.toString()));

      String expected = "{\n"
          + "  <http://www.example.org/exampleDocument#Monica> a <http://www.example.org/vocabulary#Person>;\n"
          + "    <http://www.example.org/vocabulary#friendOf> <http://www.example.org/exampleDocument#John> .\n"
          + "}";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TRIG, response.rawContent(), RDFFormat.TRIG));

    }
  }

  @Test
  public void testNodeByUriWithGraphUriOnQuadRDFTrig() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CALL semantics.importQuadRDF('" +
                RDFImportTest.class.getClassLoader().getResource("RDFDatasets/RDFDataset.trig")
                    .toURI()
                + "','TriG',{ handleVocabUris: 'KEEP', typesToLabels: true, commitSize: 500, keepCustomDataTypes: true, handleMultival: 'ARRAY'})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "application/trig").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder
              .encode("http://www.example.org/exampleDocument#Monica",
                  StandardCharsets.UTF_8.toString())
              + "?graphUri=http://www.example.org/exampleDocument%23G1");

      String expected = "<http://www.example.org/exampleDocument#G1> {\n"
          + "  <http://www.example.org/exampleDocument#Monica> <http://www.example.org/vocabulary#name>\n"
          + "      \"Monica Murphy\";\n"
          + "    <http://www.example.org/vocabulary#homepage> <http://www.monicamurphy.org>;\n"
          + "    <http://www.example.org/vocabulary#knows> <http://www.example.org/exampleDocument#John>;\n"
          + "    <http://www.example.org/vocabulary#hasSkill> <http://www.example.org/vocabulary#Management>,\n"
          + "      <http://www.example.org/vocabulary#Programming>;\n"
          + "    <http://www.example.org/vocabulary#email> <mailto:monica@monicamurphy.org> .\n"
          + "}";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TRIG, response.rawContent(), RDFFormat.TRIG));

    }
  }

  @Test
  public void testNodeByUriWithGraphUriOnQuadRDFNQuads() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CALL semantics.importQuadRDF('" +
                RDFImportTest.class.getClassLoader().getResource("RDFDatasets/RDFDataset.nq")
                    .toURI()
                + "','N-Quads',{ handleVocabUris: 'KEEP', typesToLabels: true, commitSize: 500, keepCustomDataTypes: true, handleMultival: 'ARRAY'})");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      HTTP.Response response = HTTP.withHeaders("Accept", "application/n-quads").GET(
          HTTP.GET(server.httpURI().resolve("rdf").toString()).location()
              + "describe/uri/" + URLEncoder
              .encode("http://www.example.org/exampleDocument#Monica",
                  StandardCharsets.UTF_8.toString())
              + "?graphUri=http://www.example.org/exampleDocument%23G1");
      String expected =
          "<http://www.example.org/exampleDocument#Monica> <http://www.example.org/vocabulary#name> \"Monica Murphy\" <http://www.example.org/exampleDocument#G1> .\n"
              + "<http://www.example.org/exampleDocument#Monica> <http://www.example.org/vocabulary#homepage> <http://www.monicamurphy.org> <http://www.example.org/exampleDocument#G1> .\n"
              + "<http://www.example.org/exampleDocument#Monica> <http://www.example.org/vocabulary#knows> <http://www.example.org/exampleDocument#John> <http://www.example.org/exampleDocument#G1> .\n"
              + "<http://www.example.org/exampleDocument#Monica> <http://www.example.org/vocabulary#hasSkill> <http://www.example.org/vocabulary#Management> <http://www.example.org/exampleDocument#G1> .\n"
              + "<http://www.example.org/exampleDocument#Monica> <http://www.example.org/vocabulary#hasSkill> <http://www.example.org/vocabulary#Programming> <http://www.example.org/exampleDocument#G1> .\n"
              + "<http://www.example.org/exampleDocument#Monica> <http://www.example.org/vocabulary#email> <mailto:monica@monicamurphy.org> <http://www.example.org/exampleDocument#G1> .";

      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.NQUADS, response.rawContent(), RDFFormat.NQUADS));

    }
  }

  @Test
  public void testCypherOnQuadRDFAfterDeleteRDFBNodes() throws Exception {
    // Given
    try (ServerControls server = getServerBuilder()
        .withProcedure(RDFImport.class)
        .withExtension("/rdf", RDFEndpoint.class)
        .withFixture(graphDatabaseService -> {
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CREATE INDEX ON :Resource(uri)");

            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          try (Transaction tx = graphDatabaseService.beginTx()) {
            graphDatabaseService.execute("CALL semantics.importQuadRDF('" +
                RDFImportTest.class.getClassLoader().getResource(
                    "RDFDatasets/RDFDatasetBNodes.trig")
                    .toURI()
                + "','TriG',{keepLangTag: true, handleVocabUris: 'KEEP', handleMultival: 'ARRAY', keepCustomDataTypes: true})");
            Result res = graphDatabaseService.execute("CALL semantics.deleteQuadRDF('" +
                RDFImportTest.class.getClassLoader().getResource(
                    "RDFDatasets/RDFDatasetBNodesDelete.trig")
                    .toURI()
                + "','TriG',{keepLangTag: true, handleVocabUris: 'KEEP', handleMultival: 'ARRAY', keepCustomDataTypes: true})");
            Map map = res.next();
            assertEquals(3L, map.get("triplesDeleted"));
            assertEquals(
                "4 of the statements could not be deleted, due to containing a blank node.",
                map.get("extraInfo"));
            tx.success();
          } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
          }
          return null;
        })
        .newServer()) {

      Map<String, String> params = new HashMap<>();
      params.put("cypher", "MATCH (a:Resource) "
          + "OPTIONAL MATCH (a)-[r]->()"
          + "RETURN DISTINCT *");

      HTTP.Response response = HTTP.
          withHeaders("Accept", "application/trig")
          .POST(
              HTTP.GET(server.httpURI().resolve("rdf").toString()).location() + "cypheronrdf",
              params);

      String expected = Resources
          .toString(Resources.getResource("RDFDatasets/RDFDatasetBNodesPostDeletion.trig"),
              StandardCharsets.UTF_8);
      assertEquals(200, response.status());
      assertTrue(ModelTestUtils
          .compareModels(expected, RDFFormat.TRIG, response.rawContent(), RDFFormat.TRIG));

    }
  }

  private TestServerBuilder getServerBuilder() throws IOException {
    TestServerBuilder serverBuilder = TestServerBuilders.newInProcessBuilder();
    serverBuilder.withConfig(LegacySslPolicyConfig.certificates_directory.name(),
        ServerTestUtils.getRelativePath(getSharedTestTemporaryFolder(),
            LegacySslPolicyConfig.certificates_directory));
    return serverBuilder;
  }
}
