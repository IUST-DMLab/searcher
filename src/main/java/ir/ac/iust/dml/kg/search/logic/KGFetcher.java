package ir.ac.iust.dml.kg.search.logic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import ir.ac.iust.dml.kg.raw.utils.ConfigReader;
import ir.ac.iust.dml.kg.search.logic.data.Triple;
import ir.ac.iust.dml.kg.virtuoso.jena.driver.VirtGraph;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by ali on 4/16/17.
 */
public class KGFetcher {
    private static final String KB_PREFIX = "http://fkg.iust.ac.ir/resource/";
    //private VirtGraph graph = null;
    private Model model = null;
    private static Map<String,String> interns = new ConcurrentHashMap<>(7*1000*1000);
    private Multimap<String, Triple> subjTripleMap = HashMultimap.create(2300*1000,1);
    private Multimap<String, Triple> objTripleMap = HashMultimap.create(2300*1000,1);

    public KGFetcher() throws IOException {
        System.err.println("Loading KGFetcher...");
        long t1 = System.currentTimeMillis();
        final String virtuosoServer = ConfigReader.INSTANCE.getString("virtuoso.address", "localhost:1111");
        final String virtuosoUser = ConfigReader.INSTANCE.getString("virtuoso.user", "dba");
        final String virtuosoPass = ConfigReader.INSTANCE.getString("virtuoso.password", "fkgVIRTUOSO2017");
        //graph = new VirtGraph("http://fkg.iust.ac.ir/new", "jdbc:virtuoso://" + virtuosoServer, virtuosoUser, virtuosoPass);
        //model = ModelFactory.createModelForGraph(graph);
        loadFromTTL("ttls");
        System.err.printf("KGFetcher loaded in %,d ms\n", (System.currentTimeMillis() - t1));
    }

    public String fetchLabel(String uri, boolean filterNonPersian) {
        String queryString =
                "SELECT ?o \n" +
                        "WHERE {\n" +
                        "<" +
                        uri +
                        "> <http://www.w3.org/2000/01/rdf-schema#label> ?o. \n";
        if (filterNonPersian)
            queryString += "FILTER (lang(?o) = \"fa\")";

        queryString += "}";

        final Query query = QueryFactory.create(queryString);
        final QueryExecution qexec = QueryExecutionFactory.create(query, model);
        final ResultSet results = qexec.execSelect();

        String resultText = "";
        while (results.hasNext()) {
            final QuerySolution binding = results.nextSolution();
            final RDFNode o = binding.get("o");
            resultText += o.toString();
            if (results.hasNext())
                resultText += "، ";
        }
        resultText = resultText.replaceAll("@fa", "");
        if (resultText.equals(""))
            resultText = Util.iriToLabel(uri);

        if (resultText.contains(KB_PREFIX))
            resultText = resultText.replace(KB_PREFIX, "").replace('_', ' ');

        //close connection
        try { qexec.close(); } catch (Throwable th) { th.printStackTrace(); }

        return resultText;
    }

    public String fetchWikiPage(String uri) {
        String queryString =
                "SELECT ?o \n" +
                        "WHERE {\n" +
                        "<" +
                        uri +
                        "> <http://fkg.iust.ac.ir/ontology/wikipageredirects> ?o. \n" +
                        "}";

        final Query query = QueryFactory.create(queryString);
        final QueryExecution qexec = QueryExecutionFactory.create(query, model);
        final ResultSet results = qexec.execSelect();

        String resultText = null;
        if (results.hasNext()) {
            final QuerySolution binding = results.nextSolution();
            final RDFNode o = binding.get("o");
            resultText = o.toString();
        }

        //close connection
        try { qexec.close(); } catch (Throwable th) { th.printStackTrace(); }

        return resultText;
    }

    /**s
     * Returns the (uri,label) pairs for each object statisfying subj-property-object-
     * @param subjectUri
     * @param propertyUri
     * @return
     */
    public Map<String, String> fetchSubjPropObjQuery(String subjectUri, String propertyUri) {
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
        Set<String> objectUris = subjTripleMap.get(subjectUri).stream().filter(t -> t.getPredicate().equals(propertyUri)).map(t -> t.getObject()).collect(Collectors.toSet());
        Set<String> reverseMatchUris = objTripleMap.get(subjectUri).stream().filter(t -> t.getPredicate().equals(propertyUri)).map(t -> t.getSubject()).collect(Collectors.toSet());
        objectUris.addAll(reverseMatchUris);
        for(String objectUri : objectUris){
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
                e.printStackTrace();
            }
            if (objectLabel == null || objectLabel.isEmpty()) {
                System.err.println("Lable for \"" + objectUri + "\" fetched from DB and/or resourceExtractor is null/empty, using Iri instead");
                objectLabel = objectUri;
            }

            System.out.printf("++FOUND URI: %s" + objectUri);
            System.out.printf("++FOUND LABEL: %s" + objectLabel);

            matchedObjectLabels.put(objectUri.replace("@fa",""), objectLabel.replace("@fa",""));
        }
        //close connection
            /*try { qexec.close(); } catch (Throwable th) { th.printStackTrace(); }*/
        //}
        return matchedObjectLabels;
    }

    public long fetchsubjPropertyObjRecords(long page, long pageSize) {
        String queryString =
                "SELECT ?s ?p ?o\n" +
                        "WHERE {\n" +
                        "?s ?p ?o .\n" +
                        "filter(regex(str(?p), \"/ontology/\") || regex(str(?p), \"/property/\") )\n" +
                        "}" +
                        ((page >= 0 && pageSize >= 0) ? " OFFSET " + (page * pageSize) + " LIMIT " + pageSize : "");
        System.out.println(queryString);
        System.exit(0);
        final Query query = QueryFactory.create(queryString);
        final QueryExecution exec = QueryExecutionFactory.create(query, model);
        final ResultSet results = exec.execSelect();

        long recordNum = 0;
        while (results.hasNext()) {
            recordNum++;
            final QuerySolution binding = results.nextSolution();
            final Resource s = (Resource) binding.get("s");
            final Resource p = (Resource) binding.get("p");
            final RDFNode o = binding.get("o");
            System.out.printf("[%,d]\t%s\t%s\t%s\n", recordNum + page * pageSize, s.toString(),p.toString(),o.toString());
        }
        return recordNum;
    }

    public void loadFromTTL(String folderPath) throws IOException {
        File folder = new File(folderPath);
        File[] files=folder.listFiles();
        Arrays.sort(files);
        long count = 0;
        long t = System.currentTimeMillis();

        for(File file : files){
            Model model=ModelFactory.createDefaultModel();
            model.read(new FileInputStream(file.getAbsolutePath()),null,"TTL");
            StmtIterator iter = model.listStatements();
            while ( iter.hasNext() ) {
                Statement stmt = iter.next();
                String s = intern(stmt.getSubject().toString());
                String p = intern(stmt.getPredicate().toString());
                String o = intern(stmt.getObject().toString());
                Triple triple = new Triple(s,p,o);
                subjTripleMap.put(s,triple);
                if(o.contains("http://"))
                    objTripleMap.put(o,triple);
                //System.out.printf("%,d\t%s\t%s\t%s\t%s\n", ++count,file.toString(),s,p,o);
                count++;
            }
            if ( iter != null ) iter.close();
            System.out.printf("Finished loading %s in %,d ms from beginning\n", file.getName(), System.currentTimeMillis() - t);
        }
        System.out.printf("Finished loading %,d triples in %,d ms \n", count, System.currentTimeMillis() - t);
        /*serialize(subjTripleMap,"subjTripleMap.data");
        System.out.printf("Finished subjTripleMap serialization in: %,d ms \n", System.currentTimeMillis() - t);
        serialize(objTripleMap,"objTripleMap.data");
        System.out.printf("Finished objTripleMap serialization in: %,d ms \n", System.currentTimeMillis() - t);*/
        interns.clear();
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
    private static String intern(String str){
        if(!interns.containsKey(str)) {
            interns.put(str,str);
        }
        return interns.get(str);
    }

    public static void main(String[] args) throws IOException {
        KGFetcher fetcher = new KGFetcher();;
        fetcher.loadFromTTL(args[0]);
    }
}
