package ir.ac.iust.dml.kg.search.logic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ir.ac.iust.dml.kg.raw.utils.ConfigReader;
import ir.ac.iust.dml.kg.search.logic.data.ResultEntity;
import ir.ac.iust.dml.kg.search.logic.data.Triple;
import ir.ac.iust.dml.kg.search.logic.recommendation.Recommendation;
import ir.ac.iust.dml.kg.search.logic.recommendation.RecommendationLoader;
import org.apache.commons.collections4.KeyValue;
import org.apache.jena.atlas.lib.tuple.Tuple2;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ali on 4/16/17.
 */
public class KGFetcher {
    private static final String KB_PREFIX = "http://fkg.iust.ac.ir/resource/";
    private static Map<String,String> interns = new ConcurrentHashMap<>(7*1000*1000);
    public Multimap<String, Triple> subjTripleMap = HashMultimap.create(2300*1000,1);
    public Multimap<String, Triple> objTripleMap = HashMultimap.create(2300*1000,1);

    public Map<String, Recommendation[]> getRecommendationsMap() {
        return recommendationsMap;
    }

    private Map<String, Recommendation[]> recommendationsMap = null;


    public double getRank(String uri){
        double rank = 0;

        if(uri == null || !uri.startsWith("http://"))
            return 0;

        if(subjTripleMap.containsKey(uri))
            rank += subjTripleMap.get(uri).size();

        if(objTripleMap.containsKey(uri))
            rank += objTripleMap.get(uri).size();

        return rank;
    }

    public KGFetcher() throws IOException, SQLException {
        System.err.println("Loading KGFetcher...");
        long t1 = System.currentTimeMillis();
        final String virtuosoServer = ConfigReader.INSTANCE.getString("virtuoso.address", "localhost:1111");
        final String virtuosoUser = ConfigReader.INSTANCE.getString("virtuoso.user", "dba");
        final String virtuosoPass = ConfigReader.INSTANCE.getString("virtuoso.password", "fkgVIRTUOSO2017");
        //graph = new VirtGraph("http://fkg.iust.ac.ir/new", "jdbc:virtuoso://" + virtuosoServer, virtuosoUser, virtuosoPass);
        //model = ModelFactory.createModelForGraph(graph);
        loadFromTTL(ConfigReader.INSTANCE.getString("ttlDirectory","ttls"));
        System.out.println("Loading recommendations");
        recommendationsMap = RecommendationLoader.read();
        System.err.printf("KGFetcher loaded in %,d ms\n", (System.currentTimeMillis() - t1));
        interns.clear();
    }

//    public String fetchLabel(String uri, boolean filterNonPersian) {
//        String queryString =
//                "SELECT ?o \n" +
//                        "WHERE {\n" +
//                        "<" +
//                        uri +
//                        "> <http://www.w3.org/2000/01/rdf-schema#label> ?o. \n";
//        if (filterNonPersian)
//            queryString += "FILTER (lang(?o) = \"fa\")";
//
//        queryString += "}";
//    }

//    public String fetchWikiPage(String uri) {
//        String queryString =
//                "SELECT ?o \n" +
//                        "WHERE {\n" +
//                        "<" +
//                        uri +
//                        "> <http://fkg.iust.ac.ir/ontology/wikipageredirects> ?o. \n" +
//                        "}";
//    }

    /**s
     * Returns the (uri,label) pairs for each object statisfying subj-property-object-
     * @param subjectUri
     * @param propertyUri
     * @param searchDirection
     * @return
     */
    public Map<String, String> fetchSubjPropObjQuery(String subjectUri, String propertyUri, SearchDirection searchDirection) {
        Map<String, String> matchedObjectLabels = new TreeMap<String, String>();
       /* String[] queryStrings = new String[2];
        queryStrings[0] =
                "SELECT ?o " + //?l " +
                        "WHERE {\n" +
                        "<" +
                        subjectUri +
                        "> <" + propertyUri + "> ?o. \n" +
                        //"?o <http://www.w3.org/2000/01/rdf-schema#label> ?l\n" +
                        //"FILTER (lang(?o) = \"fa\")" +
                        "}";
        queryStrings[1] =   //reverse relation
                "SELECT ?o " + //?l " +
                        "WHERE {\n" +
                        "?o <" + propertyUri +
                        "> <" + subjectUri +
                        ">. \n" +
                        //"?o <http://www.w3.org/2000/01/rdf-schema#label> ?l\n" +
                        //"FILTER (lang(?o) = \"fa\")" +
                        "}";

        for(String queryString: queryStrings) {
            final Query query = QueryFactory.create(queryString);
            final QueryExecution qexec = QueryExecutionFactory.create(query, model);
            final ResultSet results = qexec.execSelect();

            while (results.hasNext()) {
                System.err.println("HEY\nHEY\n  RESULT HAS NEXT!! \n\n HEY!!!!!!!");
                final QuerySolution binding = results.nextSolution();
                final RDFNode o = binding.get("o");
                String objectUri = o.toString();*/
        Set<String> resultUris = new LinkedHashSet<>();
        if(searchDirection == SearchDirection.SUBJ_PROP || searchDirection == SearchDirection.BOTH)
            resultUris.addAll(subjTripleMap.get(subjectUri).stream().filter(t -> t.getPredicate().equals(propertyUri)).map(t -> t.getObject()).collect(Collectors.toSet()));
        if(searchDirection == SearchDirection.PROP_SUBJ || searchDirection == SearchDirection.BOTH)
            resultUris.addAll(objTripleMap.get(subjectUri).stream().filter(t -> t.getPredicate().equals(propertyUri)).map(t -> t.getSubject()).collect(Collectors.toSet()));

        int relationCounter = 1;
        while(true) {
            String subjectRelatedUri = subjectUri + "/relation_" + relationCounter++;
            if(!subjTripleMap.containsKey(subjectRelatedUri))
                break;
            if (searchDirection == SearchDirection.SUBJ_PROP || searchDirection == SearchDirection.BOTH) {
                Set<String> relationBasedResults = (subjTripleMap.get(subjectRelatedUri)
                        .stream()
                        .filter(t -> t.getPredicate().equals(propertyUri))
                        .map(t -> t.getObject())
                        .collect(Collectors.toSet()));

                for(String relationBasedResult : relationBasedResults){
                    System.err.printf("Generating info for relational result: (subj: %s ,\t property: %s , result: %s)\n", subjectRelatedUri, propertyUri, relationBasedResult);
                    List<String> info = new ArrayList<>();

                    List<Triple> otherTriplesForThisCollectionalResult = subjTripleMap.get(subjectRelatedUri)
                            .stream()
                            .filter(t -> !(t.getPredicate().contains("22-rdf-syntax-ns#type") || t.getPredicate().contains("/mainPredicate")))
                            .collect(Collectors.toList());

                    for (Triple t : otherTriplesForThisCollectionalResult){
                        System.err.printf("\t\tRelational Result: The info pair for collection is: subj: %s \t pred: %s \t obj: %s\n",t.getSubject(),t.getPredicate(),t.getObject());
                        // Find label of predicate, if any
                        String predLabel = Util.iriToLabel(t.getPredicate());
                        if(subjTripleMap.containsKey(t.getPredicate())) {
                            List<String> labels = subjTripleMap.get(t.getPredicate()).stream().filter(v -> v.getPredicate().equals("http://www.w3.org/2000/01/rdf-schema#label")).map(v -> v.getObject()).collect(Collectors.toList());
                            if(labels.size() > 0)
                                predLabel = labels.get(0);
                        }

                        // Find label of object, if any
                        String objLabel = Util.iriToLabel(t.getObject());
                        if(subjTripleMap.containsKey(t.getObject())) {
                            List<String> labels = subjTripleMap.get(t.getObject()).stream().filter(v -> v.getPredicate().equals("http://www.w3.org/2000/01/rdf-schema#label")).map(v -> v.getObject()).collect(Collectors.toList());
                            if(labels.size() > 0)
                                objLabel = labels.get(0);
                        }
                        info.add(predLabel + ":" + objLabel);
;                    }

                    resultUris.add(relationBasedResult + " (" + info.stream().reduce((a,b) -> a + "," + b).toString().substring(1) + ")");
                }

            }
            if (searchDirection == SearchDirection.PROP_SUBJ || searchDirection == SearchDirection.BOTH)
                resultUris.addAll(objTripleMap.get(subjectRelatedUri).stream().filter(t -> t.getPredicate().equals(propertyUri)).map(t -> t.getSubject()).collect(Collectors.toSet()));
        }


        for(String objectUri : resultUris){
            if(matchedObjectLabels.containsKey(objectUri))
                continue;

            String objectLabel = objectUri;
            try {
                objectLabel = Searcher.getInstance().getExtractor().getResourceByIRI(objectUri).getLabel();
                /*if (objectLabel == null || objectLabel.isEmpty()) {
                    System.err.println("Lable for \"" + objectUri + "\" fetched from resourceExtractor is null/empty, trying DB");
                    objectLabel = fetchLabel(objectUri, false);
                }*/

            } catch (Exception e) {
                //e.printStackTrace();
                System.err.println("No lablel found in ResourceExtractor for: " + objectUri);
            }
            if (objectLabel == null || objectLabel.isEmpty()) {
                System.err.println("Lable for \"" + objectUri + "\" fetched from DB and/or resourceExtractor is null/empty, using Iri instead");
                objectLabel = objectUri;
            }

            if(objectUri.contains("("))
                objectLabel = Util.iriToLabel(objectUri);

            System.out.println("++FOUND URI: " + objectUri);
            System.out.println("++FOUND LABEL: " + objectLabel);

            matchedObjectLabels.put(objectUri.replace("@fa","").replaceAll("@en", ""), objectLabel.replace("@fa","").replaceAll("@en", ""));
        }
        //close connection
            /*try { qexec.close(); } catch (Throwable th) { th.printStackTrace(); }*/
        //}
        return matchedObjectLabels;
    }

//    public long fetchsubjPropertyObjRecords(long page, long pageSize) {
//        String queryString =
//                "SELECT ?s ?p ?o\n" +
//                        "WHERE {\n" +
//                        "?s ?p ?o .\n" +
//                        "filter(regex(str(?p), \"/ontology/\") || regex(str(?p), \"/property/\") )\n" +
//                        "}" +
//                        ((page >= 0 && pageSize >= 0) ? " OFFSET " + (page * pageSize) + " LIMIT " + pageSize : "");
//    }

    public void loadFromTTL(String folderPath) throws IOException {
        System.out.println("Loading TTLs from: " + folderPath);
        File folder = new File(folderPath);
        File[] files=folder.listFiles();
        Arrays.sort(files);
        final long[] count = {0};
        long t = System.currentTimeMillis();

        Arrays.stream(files).parallel().forEach(file ->
        {
            if(file.getName().contains("export.sh"))
                return; //بیخیال فایل مجید بشیم!

            Model model = ModelFactory.createDefaultModel();
            try {
                model.read(new FileInputStream(file.getAbsolutePath()), null, "TTL");
            } catch (Throwable e) {
                System.err.println("ERROR while loading " + file.getAbsolutePath() + "\t ... Exiting!");
                e.printStackTrace();
//                System.exit(0);
            }
            StmtIterator iter = model.listStatements();
            while (iter.hasNext()) {
                Statement stmt = iter.next();

                putTripleInMapsSynchronized(stmt);
                //System.out.printf("%,d\t%s\t%s\t%s\t%s\n", ++count,file.toString(),s,p,o);
                count[0]++;
            }
            if (iter != null) iter.close();
            System.out.printf("Finished loading %s in %,d ms from beginning\n", file.getName(), System.currentTimeMillis() - t);
        });
        System.out.printf("Finished loading %,d triples in %,d ms \n", count[0], System.currentTimeMillis() - t);
        /*serialize(subjTripleMap,"subjTripleMap.data");
        System.out.printf("Finished subjTripleMap serialization in: %,d ms \n", System.currentTimeMillis() - t);
        serialize(objTripleMap,"objTripleMap.data");
        System.out.printf("Finished objTripleMap serialization in: %,d ms \n", System.currentTimeMillis() - t);*/
    }

    private synchronized void putTripleInMapsSynchronized(Statement stmt) {
        String s = intern(stmt.getSubject().toString());
        String p = intern(stmt.getPredicate().toString());
        String o = intern(stmt.getObject().toString());
        Triple triple = new Triple(s,p,o);
        subjTripleMap.put(s,triple);
        if(o.contains("http://"))
            objTripleMap.put(o,triple);
    }

    private void serialize(Object obj, String filePath) throws IOException {
        FileOutputStream fileOut = new FileOutputStream(filePath);
        ObjectOutputStream out = new ObjectOutputStream(fileOut);
        out.writeObject(obj);
        out.close();
        fileOut.close();
    }

    /**
     * String deduplication.
     * @param str
     * @return
     */
    public static String intern(String str) {
        if(!interns.containsKey(str)) {
            interns.put(str,str);
        }
        return interns.get(str);
    }

    public static void main(String[] args) throws IOException {
        //KGFetcher fetcher = new KGFetcher();;
        //fetcher.loadFromTTL(args[0]);
        KGFetcher.loadRecommendations("C:\\Users\\ali\\Downloads\\recommendations2_sample.json");
    }

    public static void loadRecommendations(String path) throws IOException {
        List<String> texts = Files.readAllLines(Paths.get(path));
        for(String t : texts) {
            JsonElement jelement = new JsonParser().parse(t);
            JsonObject jobject = jelement.getAsJsonObject();
            System.out.println();
        }
    }

    public List<String> fetchPhotoUrls(String link) {
        List<String> photos = new ArrayList<>();
        try{
            subjTripleMap.get(link).stream()
                    .filter(t -> t.getPredicate().equals("http://fkg.iust.ac.ir/ontology/picture"))
                    .forEach(t -> photos.add(t.getObject()));
        }catch(Exception e){
            e.printStackTrace();
        }
        return photos;
    }

    public Multiset<String> getRecommendationsUri(String uri) {
        System.out.println("Computing recommendations for " + uri);
        Set<String> neighbors = getNeighbors(uri);
        Multiset<String> relevants = HashMultiset.create();
        relevants.addAll(neighbors);
        for(String nb : neighbors){
            relevants.addAll(getNeighbors(nb));
        }

        return relevants;
    }

    public Set<String> getNeighbors(String uri) {
        int LIMIT = 100;
        List<ResultEntity> resultEntities = new ArrayList<>();
        List<String> triples = new ArrayList<>();
        triples.addAll(subjTripleMap.get(uri).stream().limit(LIMIT).map(t -> t.getObject()).collect(Collectors.toSet()));
        triples.addAll(objTripleMap.get(uri).stream().limit(LIMIT).map(t -> t.getSubject()).collect(Collectors.toSet()));
        Set<String> result = triples.stream().filter(s -> s.contains("/resource/")).filter(s -> !s.equals(uri)).collect(Collectors.toSet());
        System.out.printf("Neighbors for %s \t =  %d\n", uri, result.size() );
        return result;
    }
}