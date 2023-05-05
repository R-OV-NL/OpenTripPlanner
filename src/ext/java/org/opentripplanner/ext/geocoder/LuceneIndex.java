package org.opentripplanner.ext.geocoder;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene95.Lucene95Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.suggest.document.Completion90PostingsFormat;
import org.apache.lucene.search.suggest.document.CompletionAnalyzer;
import org.apache.lucene.search.suggest.document.ContextQuery;
import org.apache.lucene.search.suggest.document.ContextSuggestField;
import org.apache.lucene.search.suggest.document.PrefixCompletionQuery;
import org.apache.lucene.search.suggest.document.SuggestIndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.opentripplanner.framework.geometry.WgsCoordinate;
import org.opentripplanner.framework.i18n.I18NString;
import org.opentripplanner.framework.i18n.NonLocalizedString;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.site.StopLocationsGroup;
import org.opentripplanner.transit.service.TransitService;

public class LuceneIndex implements Serializable {

  private static final String TYPE = "type";
  private static final String ID = "id";
  private static final String SUGGEST = "suggest";
  private static final String NAME = "name";
  private static final String CODE = "code";
  private static final String LAT = "latitude";
  private static final String LON = "longitude";

  private final Graph graph;

  private final TransitService transitService;
  private final Analyzer analyzer;
  private final SuggestIndexSearcher searcher;

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

  public LuceneIndex(Graph graph, TransitService transitService) {
    this.graph = graph;
    this.transitService = transitService;
    this.analyzer =
      new PerFieldAnalyzerWrapper(
        new StandardAnalyzer(),
        Map.of(NAME, new SimpleAnalyzer(), SUGGEST, new CompletionAnalyzer(new StandardAnalyzer()))
      );

    var directory = new ByteBuffersDirectory();

    try {
      try (
        var directoryWriter = new IndexWriter(
          directory,
          iwcWithSuggestField(analyzer, Set.of(SUGGEST))
        )
      ) {
        transitService
          .listStopLocations()
          .stream()
          .filter(distinctByKey(sl -> sl.getCoordinate().roundToApproximate10m()))
          .filter(distinctByKey(StopLocation::getName))
          .forEach(stopLocation ->
            addToIndex(
              directoryWriter,
              StopLocation.class,
              stopLocation.getId().toString(),
              stopLocation.getName(),
              stopLocation.getCode(),
              stopLocation.getCoordinate().latitude(),
              stopLocation.getCoordinate().longitude()
            )
          );

        transitService
          .listStopLocationGroups()
          .forEach(stopLocationsGroup ->
            addToIndex(
              directoryWriter,
              StopLocationsGroup.class,
              stopLocationsGroup.getId().toString(),
              stopLocationsGroup.getName(),
              null,
              stopLocationsGroup.getCoordinate().latitude(),
              stopLocationsGroup.getCoordinate().longitude()
            )
          );

        buildStopClusters(
          transitService.listStopLocations(),
          transitService.listStopLocationGroups()
        )
          .forEach(stopCluster -> {
            addToIndex(
              directoryWriter,
              StopCluster.class,
              stopCluster.id().toString(),
              new NonLocalizedString(stopCluster.name()),
              stopCluster.code(),
              stopCluster.coordinate().latitude(),
              stopCluster.coordinate().longitude()
            );
          });

        graph
          .getVertices()
          .stream()
          .filter(v -> v instanceof StreetVertex)
          .map(v -> (StreetVertex) v)
          .forEach(streetVertex ->
            addToIndex(
              directoryWriter,
              StreetVertex.class,
              streetVertex.getLabel(),
              streetVertex.getIntersectionName(),
              streetVertex.getLabel(),
              streetVertex.getLat(),
              streetVertex.getLon()
            )
          );
      }

      DirectoryReader indexReader = DirectoryReader.open(directory);
      searcher = new SuggestIndexSearcher(indexReader);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Stream<StopCluster> buildStopClusters(
    Collection<StopLocation> stopLocations,
    Collection<StopLocationsGroup> stopLocationsGroups
  ) {
    var stops = stopLocations
      .stream()
      .filter(sl -> sl.getParentStation() == null)
      .flatMap(sl -> StopCluster.of(sl).stream());
    var stations = stopLocationsGroups.stream().map(StopCluster::of);

    return Stream.concat(stops, stations);
  }

  public static synchronized LuceneIndex forServer(OtpServerRequestContext serverContext) {
    var graph = serverContext.graph();
    var existingIndex = graph.getLuceneIndex();
    if (existingIndex != null) {
      return existingIndex;
    }

    var newIndex = new LuceneIndex(graph, serverContext.transitService());
    graph.setLuceneIndex(newIndex);
    return newIndex;
  }

  public Stream<StopLocation> queryStopLocations(String query, boolean autocomplete) {
    return matchingDocuments(StopLocation.class, query, autocomplete)
      .map(document -> transitService.getStopLocation(FeedScopedId.parseId(document.get(ID))));
  }

  public Stream<StopLocationsGroup> queryStopLocationGroups(String query, boolean autocomplete) {
    return matchingDocuments(StopLocationsGroup.class, query, autocomplete)
      .map(document -> transitService.getStopLocationsGroup(FeedScopedId.parseId(document.get(ID)))
      );
  }

  public Stream<StreetVertex> queryStreetVertices(String query, boolean autocomplete) {
    return matchingDocuments(StreetVertex.class, query, autocomplete)
      .map(document -> (StreetVertex) graph.getVertex(document.get(ID)));
  }

  /**
   * Return all "stop clusters" for a given query.
   * <p>
   * Stop clusters are defined as follows.
   * <p>
   *  - If a stop has a parent station, only the parent is returned.
   *  - If two stops have the same name *and* are less than 10 meters from each other, only
   *    one of those is chosen at random and returned.
   */
  public Stream<StopCluster> queryStopClusters(String query, boolean autocomplete) {
    return matchingDocuments(StopCluster.class, query, autocomplete)
      .map(LuceneIndex::toStopCluster);
  }

  private static StopCluster toStopCluster(Document document) {
    var id = FeedScopedId.parseId(document.get(ID));
    var name = document.get(NAME);
    var code = document.get(CODE);
    var lat = document.getField(LAT).numericValue().doubleValue();
    var lon = document.getField(LON).numericValue().doubleValue();
    return new StopCluster(id, code, name, new WgsCoordinate(lat, lon));
  }

  static IndexWriterConfig iwcWithSuggestField(Analyzer analyzer, final Set<String> suggestFields) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    Codec filterCodec = new Lucene95Codec() {
      final PostingsFormat postingsFormat = new Completion90PostingsFormat();

      @Override
      public PostingsFormat getPostingsFormatForField(String field) {
        if (suggestFields.contains(field)) {
          return postingsFormat;
        }
        return super.getPostingsFormatForField(field);
      }
    };
    iwc.setCodec(filterCodec);
    return iwc;
  }

  private static void addToIndex(
    IndexWriter writer,
    Class<?> type,
    String id,
    I18NString name,
    @Nullable String code,
    double latitude,
    double longitude
  ) {
    String typeName = type.getSimpleName();

    Document document = new Document();
    document.add(new StoredField(ID, id));
    document.add(new TextField(TYPE, typeName, Store.YES));
    document.add(new TextField(NAME, Objects.toString(name), Store.YES));
    document.add(new ContextSuggestField(SUGGEST, Objects.toString(name), 1, typeName));
    document.add(new StoredField(LAT, latitude));
    document.add(new StoredField(LON, longitude));

    if (code != null) {
      document.add(new TextField(CODE, code, Store.YES));
      document.add(new ContextSuggestField(SUGGEST, code, 1, typeName));
    }

    try {
      writer.addDocument(document);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private Stream<Document> matchingDocuments(
    Class<?> type,
    String searchTerms,
    boolean autocomplete
  ) {
    try {
      if (autocomplete) {
        var completionQuery = new PrefixCompletionQuery(
          analyzer,
          new Term(SUGGEST, analyzer.normalize(SUGGEST, searchTerms))
        );
        var query = new ContextQuery(completionQuery);
        query.addContext(type.getSimpleName());

        var topDocs = searcher.suggest(query, 25, true);

        return Arrays
          .stream(topDocs.scoreDocs)
          .map(scoreDoc -> {
            try {
              return searcher.doc(scoreDoc.doc);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
      } else {
        var parser = new QueryParser(CODE, analyzer);
        var nameQuery = parser.createPhraseQuery(NAME, searchTerms);
        var codeQuery = new TermQuery(new Term(CODE, analyzer.normalize(CODE, searchTerms)));
        var typeQuery = new TermQuery(
          new Term(TYPE, analyzer.normalize(TYPE, type.getSimpleName()))
        );

        var builder = new BooleanQuery.Builder()
          .setMinimumNumberShouldMatch(1)
          .add(typeQuery, Occur.MUST)
          .add(codeQuery, Occur.SHOULD);

        if (nameQuery != null) {
          builder.add(nameQuery, Occur.SHOULD);
        }

        var query = builder.build();

        var topDocs = searcher.search(query, 25);

        return Arrays
          .stream(topDocs.scoreDocs)
          .map(scoreDoc -> {
            try {
              return searcher.doc(scoreDoc.doc);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
      }
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public record StopCluster(
    FeedScopedId id,
    @Nullable String code,
    String name,
    WgsCoordinate coordinate
  ) {
    public static StopCluster of(StopLocationsGroup g) {
      return new StopCluster(g.getId(), null, g.getName().toString(), g.getCoordinate());
    }

    static Optional<StopCluster> of(StopLocation sl) {
      return Optional
        .ofNullable(sl.getName())
        .map(name -> new StopCluster(sl.getId(), sl.getCode(), name.toString(), sl.getCoordinate())
        );
    }
  }
}
