/*
JavaEE implementation of a Linked API MeanFree for image processing, 
i.e. a Web service with ontological input and output description 
wrapping the processor. MeanFree normalizes the coloring of tissue
of a MRI brain scan.  
*/

package edu.kit.aifb.cognitiveapps;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import javax.xml.ws.http.HTTPException;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;

@Path("/MeanFree/")
public class MeanFree {

	@Context
	UriInfo uriInfo;
	@Context
	Request request;
	

	/*
	Get implementation to retrieve turtle service description
	*/

	@GET
	@Path("/")
	@Produces("text/turtle")
	public String getServiceDescriptionTTL() throws Exception {
		Supporter s = new Supporter();
		return s.readFile("meanfree_description_ttl.txt");
	}
	
	/*
	Post implementation to send scans to service and retrieve
	the normalized scan. 
	
	Params
	======
	rdf : (String)
	   RDF triples serialized in turtle
	*/
	
	@POST
	@Path("/")
	@Consumes("text/turtle")
	@Produces("text/turtle")
	public String start(
			String rdf, 
			@Context final HttpServletResponse servletResponse, 
			@Context final HttpServletRequest servletRequest, 
			@Context final ServletContext context
			) 
					throws InstantiationException, IllegalAccessException,
					IOException, HTTPException, URISyntaxException {
					
		System.setProperty("http.keepAlive", "false");
		
		Supporter s = new Supporter();			
		String prefixes = s.readFile("prefixes.txt");
		String input_pattern = s.readFile("meanfree_input.txt");
		String querystring = prefixes + " SELECT ?brainImage ?brainMask "
				+ "WHERE { " +input_pattern + " }";
		
		Model model = null;
		InputStream in = new ByteArrayInputStream(rdf.getBytes());
		model = ModelFactory.createDefaultModel();
		model.read(in, null, "TURTLE");
		in.close();
		
		Query query = QueryFactory.create(querystring);
		QueryExecution qexec = QueryExecutionFactory.create(query, model);
		
		Resource brainImage = null;
		Resource brainMask = null;
		
		List<String> resources = new ArrayList<String>();
		
		try {
			ResultSet results = qexec.execSelect();
			for (; results.hasNext();) {
				QuerySolution soln = results.nextSolution();
				brainImage = soln.getResource("brainImage");
				brainMask = soln.getResource("brainMask");
			}
			qexec.close();
		} catch(Exception e) {
		}
		
		resources.add(brainMask.getURI());	
		resources.add(brainImage.getURI());
		
		Model model2 = null;
		model2 = ModelFactory.createDefaultModel();	
		model2.read(brainImage.getURI());
		model2.read(brainMask.getURI());
		
		String brainImageLocation = s.getLocation(brainImage, model2);
		String brainMaskLocation = s.getLocation(brainMask, model2);		
		String patient = s.getPatient(brainImage, model2);
		String pid = patient.substring(patient.length()-13, 
				patient.length());
		
		String path = "/tmp/";
		String brainImageLocal = Supporter.downloadFileFromXNAT(new URI(
				brainImageLocation), path);
		String brainMaskLocal = Supporter.downloadFileFromXNAT(new URI(
				brainMaskLocation), path);				
		String server_prog = "/var/lib/tomcat7/webapps/mitk/mitk_mbi2/";
		
		DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
		Date date = new Date();
		String f1 = "output1_" + dateFormat.format(date) + ".nrrd";		
		int exit = -10000;
		
		String sp = "http://surgipedia.sfb125.de/wiki/Special:URIResolver/"
				+ "Category-3A";	
		String[] ssh = {server_prog + "mitkBrainStrippingMiniApps.sh", 
				"MeanFree", brainImageLocal, brainMaskLocal, path + f1};
		File f1f = new File(path + f1);
		f1f.getParentFile().mkdir();
		f1f.createNewFile();
		
		exit = LinuxInteractor.executeCommand("shell", ssh , true);
			
		if(exit != 0) {
			return "Execution failed.";
		}
										
		Supporter.uploadFileToXNAT(new URI("https://xnat.sfb125.de/data/"
				+ "archive/projects/TP/subjects/" +pid + "/files?format=" 
				+ sp + "NormalizedBrainImage"), path + f1);
		List<String> outputs = new ArrayList<String>();	
		outputs.add(patient + "/file/" + f1);
	
		File f1r = new File(path + "result_" + dateFormat.format(date) +
				".ttl");
		f1r.getParentFile().mkdir();
		f1r.createNewFile();
		FileWriter out = new FileWriter( f1r );
		
		try {
			Model result = s.getVocab(patient, resources, outputs, 
					dateFormat.format(date),
					"MeanFree");
		    result.write( out, "TURTLE" );
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
		   try {
		       out.close();
		   }
		   catch (IOException closeException) {
		       // ignore
		   }
		}
				
		Supporter.uploadFileToXNAT(new URI("https://xnat.sfb125.de/data/"
				+ "archive/projects/TP/subjects/" +pid + "/files?format="
						+ "Execution"), f1r.getAbsolutePath());
			
		return s.readFile2(f1r.getAbsolutePath());
	}
}
