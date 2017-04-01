package edu.kit.aifb.cognitiveapps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;

import javax.xml.ws.http.HTTPException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.vocabulary.DC;
import com.hp.hpl.jena.vocabulary.RDF;

@SuppressWarnings("deprecation")
public class Supporter {

	public String readFile(String filename) throws IOException {
		InputStream fstream = getClass().getClassLoader()
                .getResourceAsStream(filename);
		StringWriter writer = new StringWriter();
		IOUtils.copy(fstream, writer, StandardCharsets.UTF_8);
		return writer.toString();
	}
	
	public String readFile2(String fileName) throws IOException {
	    BufferedReader br = new BufferedReader(new FileReader(fileName));
	    try {
	        StringBuilder sb = new StringBuilder();
	        String line = br.readLine();
	        while (line != null) {
	            sb.append(line);
	            sb.append("\n");
	            line = br.readLine();
	        }
	        return sb.toString();
	    } finally {
	        br.close();
	    }
	}
	
	public String getLocation(Resource r, Model m) throws IOException {
		String res = "";	
		String kbQueryString = readFile("download_query.txt");
		kbQueryString = kbQueryString.replace("qwertzuiop", r.asResource().getURI());
		Query kbQuery = QueryFactory.create(kbQueryString);
		QueryExecution qe = QueryExecutionFactory.create(kbQuery, m);	
		try {
			ResultSet results = qe.execSelect();
			for (; results.hasNext();) {
				QuerySolution soln = results.nextSolution();
				Resource location = soln.getResource("location");
				res = location.getURI();
			}
			qe.close();
		} catch(Exception e) {
		
		}
		return res;
	}

	public String getPatient(Resource r, Model m) throws IOException {
		String res = "";		
		String kbQueryString = readFile("patient_query.txt");
		kbQueryString = kbQueryString.replace("qwertzuiop", r.asResource().getURI());
		Query kbQuery = QueryFactory.create(kbQueryString);
		QueryExecution qe = QueryExecutionFactory.create(kbQuery, m);
		try {
			ResultSet results = qe.execSelect();
			for (; results.hasNext();) {
				QuerySolution soln = results.nextSolution();
				Resource belonging = soln.getResource("belongsTo");
				if(belonging.getURI().contains("subject"))
					res = belonging.getURI();
			}
			qe.close();
		} catch(Exception e) {		
		}
		return res;
	}
	
	public static String uploadFileToXNAT(URI uri, String pathOnDisc) throws HTTPException, IOException {		
		DefaultHttpClient client = getNewHttpClient();
		HttpGet get = new HttpGet(uri);
		HttpPost post = new HttpPost(uri);		
		File file = new File(pathOnDisc);
		HttpEntity entity = MultipartEntityBuilder.create().addBinaryBody("data", file, ContentType.create("application/octet-stream"), file.getName()).build(); 
		post.setEntity(entity);
		String userpass = "user:pass";
		byte[] encodedBytes = Base64.encodeBase64(userpass.getBytes());
		String encoding = new String(encodedBytes);
		get.setHeader("Authorization", "Basic " + encoding);
		post.setHeader("Authorization", "Basic " + encoding);
		HttpResponse response = client.execute(post);
		if (response.getStatusLine().getStatusCode() == 404) {
			System.out.println("OH NO");
			throw new HTTPException(404);
		}
		return pathOnDisc;
	}
		
	public static String downloadFileFromXNAT(URI uri, String pathOnDisc) throws HTTPException, IOException {
		DefaultHttpClient client = getNewHttpClient();
		HttpGet get = new HttpGet(uri);
		get.setHeader("Content-Type", "application/xml");
		String extension = FilenameUtils.getExtension(uri.toString());
		int random = (int) ((Math.random()) * 999999999 + 1);
		if (extension.isEmpty()) {
			pathOnDisc = pathOnDisc + random + ".nrrd";
		} else {
			pathOnDisc = pathOnDisc + FilenameUtils.getName(uri.toString());
		}
		String userpass = "user:pass";
		byte[] encodedBytes = Base64.encodeBase64(userpass.getBytes());
		String encoding = new String(encodedBytes);
		InputStream inputstream = null;
		OutputStream outputstream = null;
		get.setHeader("Authorization", "Basic " + encoding);
		HttpResponse response = client.execute(get);
		if (response.getStatusLine().getStatusCode() == 404) {
			throw new HTTPException(404);
		}
		File file = new File(pathOnDisc);
		inputstream = response.getEntity().getContent();
		outputstream = new FileOutputStream(file);
		int read = 0;
		byte[] bytes = new byte[1024];

		while ((read = inputstream.read(bytes)) != -1) {
			outputstream.write(bytes, 0, read);
		}		if (inputstream != null) {
			try {
				inputstream.close();
			} catch (IOException e) {
			}
		}
		if (outputstream != null) {
			try {
				outputstream.close();
			} catch (IOException e) {
			}

		}

		return pathOnDisc;
	}

	
	private static DefaultHttpClient getNewHttpClient() {

		try {
			KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
			trustStore.load(null, null);
			SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
			sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			HttpParams params = new BasicHttpParams();
			HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
			HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
			SchemeRegistry registry = new SchemeRegistry();
			registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			registry.register(new Scheme("https", sf, 443));
			ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);
			return new DefaultHttpClient(ccm, params);
		} catch (Exception e) {
			return new DefaultHttpClient();
		}
	}
	
	
	public Model getVocab(String subject, List<String> inputs, List<String> outputs, String date, String algoname) {
		String sp = "http://surgipedia.sfb125.de/wiki/Special:URIResolver/";
		String xsd = "http://www.w3.org/2001/XMLSchema#";
		Model model = ModelFactory.createDefaultModel();
		model.setNsPrefix("sp", sp);
		model.setNsPrefix("xsd", xsd);
		Property hasCognitiveApp = model.createProperty(sp + "Property-3AhasCognitiveApp");
		Property hasInput = model.createProperty(sp + "Property-3AhasInput");
		Property hasOutput = model.createProperty(sp + "Property-3AhasOutput");
		Property hasDate = model.createProperty(xsd + "date");
		Property hasExecution = model.createProperty(sp + "Property-3AhasExecution");		
		Resource subjectEnt = ResourceFactory.createResource(subject);
		Resource executionEnt = model.createResource()
				.addProperty(RDF.type, model.createResource(sp +"Category-3ACognitive_App_Execution"))
				.addProperty(hasCognitiveApp, model.createResource(sp + algoname))
				.addProperty(hasDate, date);
		for(String input:inputs) {
			executionEnt.addProperty(hasInput, model.createResource(input));
		}
		for(String output:outputs) {
			executionEnt.addProperty(hasOutput, model.createResource(output));
		}
		model.add(subjectEnt, hasExecution, executionEnt);
		return model;
	}
	
	public Model getFileAnno(String subject, String type, String format) {
		String sp = "http://surgipedia.sfb125.de/wiki/Special:URIResolver/";
		Model model = ModelFactory.createDefaultModel();
		model.setNsPrefix("sp", sp);
		Resource typer = ResourceFactory.createResource(sp + "Category-3A" +type);
		Resource formatr = ResourceFactory.createResource(sp + format);
		
		model.createResource(subject).addProperty(RDF.type, typer).addProperty(DC.format, formatr);
		return model;
	}
		
}
