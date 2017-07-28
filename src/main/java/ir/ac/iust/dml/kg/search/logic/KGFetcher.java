package ir.ac.iust.dml.kg.search.logic;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import ir.ac.iust.dml.kg.raw.utils.ConfigReader;
import ir.ac.iust.dml.kg.virtuoso.jena.driver.VirtGraph;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ali on 4/16/17.
 */
public class KGFetcher {
    private static final String KB_PREFIX = "http://fkg.iust.ac.ir/resource/";
    private VirtGraph graph = null;
    private Model model = null;
    private static Map<String,String> interns = new ConcurrentHashMap<>(7*1000*1000);
    private List<String> subjects = new ArrayList<>();
    private List<String> predicates = new ArrayList<>();
    private List<String> objects = new ArrayList<>();
    private  Map<Map.Entry<String,String>,List<String>> subjPropertyObjMap = new HashMap<>();
    private  Map<Map.Entry<String,String>, List<String>> objPropertySubjMap = new HashMap<>();

    public KGFetcher() {
        System.err.println("Loading KGFetcher...");
        long t1 = System.currentTimeMillis();
        final String virtuosoServer = ConfigReader.INSTANCE.getString("virtuoso.address", "localhost:1111");
        final String virtuosoUser = ConfigReader.INSTANCE.getString("virtuoso.user", "dba");
        final String virtuosoPass = ConfigReader.INSTANCE.getString("virtuoso.password", "fkgVIRTUOSO2017");
        graph = new VirtGraph("http://fkg.iust.ac.ir/new", "jdbc:virtuoso://" + virtuosoServer, virtuosoUser, virtuosoPass);
        model = ModelFactory.createModelForGraph(graph);
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
        String[] queryStrings = new String[2];
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
                String objectUri = o.toString();
                if(matchedObjectLabels.containsKey(objectUri))
                    continue;

                String objectLabel = objectUri;
                try {
                    objectLabel = Searcher.getInstance().getExtractor().getResourceByIRI(objectUri).getLabel();
                    if (objectLabel == null || objectLabel.isEmpty()) {
                        System.err.println("Lable for \"" + objectUri + "\" fetched from resourceExtractor is null/empty, trying DB");
                        objectLabel = fetchLabel(objectUri, false);
                    }
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
            try { qexec.close(); } catch (Throwable th) { th.printStackTrace(); }
        }
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
        long count = 0;
        long t = System.currentTimeMillis();

        for(File file : files){
            Model model=ModelFactory.createDefaultModel();
            model.read(new FileInputStream(file.getAbsolutePath()),null,"TTL");
            StmtIterator iter = model.listStatements();
            while ( iter.hasNext() ) {
                Statement stmt = iter.next();
                String s = stmt.getSubject().toString();
                String p = stmt.getPredicate().toString();
                String o = stmt.getObject().toString();
                subjects.add(intern(s));
                predicates.add(intern(p));
                objects.add(intern(o));
                //writeToMap(subjPropertyObjMap,s,p,o);
                //writeToMap(objPropertySubjMap,o,p,s);
                //System.out.printf("%,d\t%s\t%s\t%s\t%s\n", ++count,file.toString(),s,p,o);

            }
            if ( iter != null ) iter.close();
            System.out.printf("Finished loading %s in %,d ms from beginning\n", file.getName(), System.currentTimeMillis() - t);
        }

        System.out.printf("Finished in: %,d ms \n", System.currentTimeMillis() - t);
        //serialize(subjPropertyObjMap,"subjPropertyObjMap.data");
        System.out.printf("Finished subjPropertyObjMap serialization in: %,d ms \n", System.currentTimeMillis() - t);
        //serialize(objPropertySubjMap,"objPropertySubjMap.data");
        System.out.printf("Finished objPropertySubjMap serialization in: %,d ms \n", System.currentTimeMillis() - t);

        System.out.printf("s:%,d\tp:%,d\to:%,d\ttotal strings:%,d\n", subjects.size(),predicates.size(),objects.size(),interns.size());

        interns.clear();
    }

    private void serialize(Map<Map.Entry<String, String>, List<String>> obj, String filePath) throws IOException {
        FileOutputStream fileOut = new FileOutputStream(filePath);
        ObjectOutputStream out = new ObjectOutputStream(fileOut);
        out.writeObject(obj);
        out.close();
        fileOut.close();
    }

    private static void writeToMap(Map<Map.Entry<String, String>, List<String>> map, String k1, String k2, String v) {
        AbstractMap.SimpleEntry<String, String> key = new AbstractMap.SimpleEntry(intern(k1), intern(k2));
        if(!map.containsKey(key))
            map.put(key,new ArrayList<>());
        if(!map.get(key).contains(v))
            map.get(key).add(intern(v));
    }

    /**
     * String deduplication.
     * @param str
     * @return
     */
    private synchronized static String intern(String str){
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
