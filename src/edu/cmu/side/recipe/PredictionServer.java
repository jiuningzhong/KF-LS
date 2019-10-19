/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.side.recipe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JOptionPane;
import org.simpleframework.http.Part;
import org.simpleframework.http.Query;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;
import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cmu.side.Workbench;
import edu.cmu.side.control.BuildModelControl;
import edu.cmu.side.model.Recipe;
import edu.cmu.side.model.RecipeManager;
import edu.cmu.side.model.StatusUpdater;
import edu.cmu.side.model.data.DocumentList;
import edu.cmu.side.model.data.FeatureTable;
import edu.cmu.side.model.data.PredictionResult;
import edu.cmu.side.model.data.ResponseJson;
import edu.cmu.side.model.data.TrainingResult;
import edu.cmu.side.model.feature.Feature;
import edu.cmu.side.model.feature.Feature.Type;
import edu.cmu.side.model.feature.FeatureHit;
import edu.cmu.side.plugin.FeaturePlugin;
import edu.cmu.side.plugin.LearningPlugin;
import edu.cmu.side.plugin.ModelMetricPlugin;
import edu.cmu.side.plugin.SIDEPlugin;
import edu.cmu.side.plugin.control.PluginManager;
import edu.cmu.side.util.MyHtmlFormatter;
import edu.cmu.side.view.util.CSVExporter;
import edu.cmu.side.view.util.DocumentListTableModel;
import edu.cmu.side.view.util.SwingUpdaterLabel;
import plugins.features.BasicFeatures;
import plugins.features.ColumnFeatures;
import plugins.learning.WekaBayes;
import plugins.learning.WekaLogit;
import plugins.learning.WekaSVM;



/**
 * loads a model trained using lightSIDE uses it to label new instances via the
 * web. TODO (maybe): allow classification of multiple instances at once, or by
 * multiple classifiers, upload new trained models (possible?)
 * 
 * @author dadamson
 */

public class PredictionServer implements Container {

    static private FileHandler fileTxt;
    static private SimpleFormatter formatterTxt;

    static private FileHandler fileHTML;
    static private Formatter formatterHTML;
    
	protected static Map<String, Predictor> predictors = new HashMap<String, Predictor>();

	private final Executor executor;
	protected static Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);


	public static void serve(int port, int threads) throws Exception {
		Container container = new PredictionServer(threads);

		Server server = new ContainerServer(container);
		Connection connection = new SocketConnection(server);
		SocketAddress address = new InetSocketAddress(port);

		connection.connect(address);
		logger.setLevel(Level.INFO);
		logger.fine("Started server on port " + port + ".");
		
		fileTxt = new FileHandler("Logging.txt", true);
        fileHTML = new FileHandler("Logging.html", true);

        // create a TXT formatter
        formatterTxt = new SimpleFormatter();
        fileTxt.setFormatter(formatterTxt);
        logger.addHandler(fileTxt);

        // create an HTML formatter	
        formatterHTML = new MyHtmlFormatter();
        fileHTML.setFormatter(formatterHTML);
        logger.addHandler(fileHTML);
	}

	@Override
	public void handle(final Request request, final Response response){
	//public void handle(HttpServletRequest request, HttpServletResponse response) throws ServletException{
		executor.execute(new Runnable() {

			@Override
			public void run() {
				handleRequest(request, response);
			}

		});
	}

	public void handleRequest(Request request, Response response) {
		try {
			PrintStream body = response.getPrintStream();
			long time = System.currentTimeMillis();

			String target = request.getTarget();
			logger.fine(request.getMethod() + ": " + target);

			String answer = null;

			//response.setValue("Content-Type", "multipart/form-data");
			response.setValue("Server", "HelloWorld/1.0 (Simple 4.0)");
			response.setValue("Access-Control-Allow-Origin", "*");
			// Request headers you wish to allow
			response.setValue("Access-Control-Allow-Headers", "Access-Control-Allow-Origin, Access-Control-Request-Method, Access-Control-Request-Headers, Access-Control-Allow-Headers, Origin, X-Requested-With, Content-Type, Accept, Authorization");
			// Request methods you wish to allow
			response.setValue("Access-Control-Allow-Methods", "POST, GET, PUT, OPTIONS, DELETE, PATCH");
			response.setDate("Date", time);
			response.setDate("Last-Modified", time);

			response.setValue("Content-Type", "application/json;charset=utf-8");
			if (target.equalsIgnoreCase("/upload")) {
				if (request.getMethod().equalsIgnoreCase("POST")) {
					answer = handleUpload(request, response);
				} else {
					answer = handleGetUpload(request, response);
				}

			}

			else if (target.equalsIgnoreCase("/uploadinput")) {
				
				if (request.getMethod().equalsIgnoreCase("POST")) {
					
					answer = handleUploadInputDocument(request, response);
					if (answer!="")
					{				
//						answer= response.getDescription();
						response.setValue("file Uploaded","Success");
						response.setValue("Accuracy",answer);
						response.setDescription(answer);
						response.setDescription("OK");
						logger.fine("response is"+response.getDescription());
					}
						
				} else {
					answer = handleGetInputDocument(request, response);
				}
			}
			
			else if (target.startsWith("/predicttest")) {
				
				if (request.getMethod().equalsIgnoreCase("POST")) {
					answer = handleTestPredict(request, response);
					if (answer!="")
					{				
//						answer= response.getDescription();
						response.setValue("file Uploaded","Success");
						response.setValue("Accuracy",answer);
						response.setDescription(answer);
						response.setDescription("OK");
						logger.fine("response is"+response.getDescription());
					}
						
				} else {
					answer = handleGetPredict(request, response);
				}
			}
			
			else if (target.equalsIgnoreCase("/uploadtest")) {
				
				if (request.getMethod().equalsIgnoreCase("POST")) {
					
					answer = handlePredictTest(request, response);
					if (answer=="Success")
					{

						answer= response.getDescription();
						
					}
						
				} else {
					answer = handleGetTestData(request, response);
				}
			}
			
			else if (target.equalsIgnoreCase("/CSVsave")) {
				
				if (request.getMethod().equalsIgnoreCase("POST")) {
					
					answer = handleCSVSave(request, response);
					if (answer!="")
					{
						response.setDescription("Predicted File");
						answer= response.getDescription();
						response.setValue("file Uploaded",answer);
					}
						
				} else {
					answer = handleCsvSave(request, response);
				}
			}

			else if (target.startsWith("/try")) {
				if (request.getMethod().equalsIgnoreCase("POST")) {
					answer = handleEvaluate(request, response);
				} else {
					answer = handleGetEvaluate(request, response, "<h1>Try it out!</h1>");
				}
			}

			else if (target.startsWith("/predict")) {
				answer = handlePredict(request, response);
			} else if (target.startsWith("/favicon.ico")) {
				answer = handleIcon(request, response, body);
			}

			if (answer == null) {
				response.setCode(404);
				body.println("There is no data, only zuul.");
			} else
			{
				body.println(answer);
				//body.println(response.getDescription());
			}
				

			int code = response.getCode();
			if (code != 200) {
				body.println("HTTP Code " + code);
				logger.fine("HTTP Code " + code);
			}

			body.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String handleIcon(Request request, Response response, PrintStream out) {
		try {
			File icon = new File("toolkits/icons/bulbs/favicon.ico");
			response.setDate("Last-Modified", icon.lastModified());
			response.setValue("Content-Type", "image/ico");
			FileInputStream in = new FileInputStream(icon);

			byte[] buffer = new byte[1024];
			int len = in.read(buffer);
			while (len != -1) {
				out.write(buffer, 0, len);
				len = in.read(buffer);
				if (Thread.interrupted()) {
					throw new InterruptedException();
				}
			}
			return "";
		} catch (Exception e) {
			response.setCode(500);
			return "Ack!";
		}

	}

	protected String handleGetUpload(Request request, Response response) {
		response.setValue("Content-Type", "text/html");
		return "<head><title>Loader</title></head><body>" + "<h1>Loader</h1>"
				+ "<form action=\"upload\" method=\"post\" enctype=\"multipart/form-data\">"
				+ "Serialized Model: <input type=\"file\" name=\"model\"><br>"
				+ "Model Nickname:<input type=\"text\" name=\"modelNick\"> "
				+ "<input type=\"submit\" name=\"Submit\" value=\"Upload Model\">" + "</form>" + "</body>";
	}

	protected String handleGetInputDocument(Request request, Response response) {
		response.setValue("Content-Type", "text/html");
		return "<head><title>SIDE Loader</title></head><body>" + "<h1>Document Loader</h1>"
				+ "<form action=\"uploadinput\" method=\"post\" enctype=\"multipart/form-data\">"
				+ "Document File: <input type=\"file\" name=\"inputfile\"><br>"
				//+ "Document Nickname:<input type=\"text\" name=\"inputNick\"> "
				+"<select name=\"algo\"><option value=\"naive\">Naive Bayes</option>"
				+"<option value=\"logistic\">Logistic Regression</option></select><br>"
				+ "<input type=\"submit\" name=\"Submit\" value=\"Upload File for Extraction\">" + "</form>" + "</body>";
	}
	
	protected String handleGetPredict(Request request, Response response) {
		response.setValue("Content-Type", "text/html");
		return "<head><title>SIDE Loader</title></head><body>" + "<h1>Document Loader</h1>"
				+ "<form action=\"predicttest\" method=\"post\" enctype=\"multipart/form-data\">"
				+ "Document File: <input type=\"file\" name=\"inputfile\"><br>"
				+ "Request ID: <input type=\"text\" name=\"requestID\" value=\"123456\"> "
				+ "JSON Request: <input type=\"text\" name=\"jsonString\" value=\"\"> "
				+ "Requestor: <input type=\"text\" name=\"requestorName\" value=\"\"> "
				+ "<br>"
				+ "<input type=\"submit\" name=\"Submit\" value=\"Upload File for Extraction\">" + "</form>" + "</body>";
	}	
	
	protected String handleGetTestData(Request request, Response response) {
		response.setValue("Content-Type", "text/html");
		return "<head><title>SIDE Loader</title></head><body>" + "<h1>Upload Test Data</h1>"
				+ "<form action=\"uploadtest\" method=\"post\" enctype=\"multipart/form-data\">"
				+ "Document File: <input type=\"file\" name=\"inputfile\"><br>"
				//+ "Document Nickname:<input type=\"text\" name=\"inputNick\"> "
				+ "<input type=\"submit\" name=\"Submit\" value=\"Predict\">" + "</form>" + "</body>";
	}
	
	protected String handleCsvSave(Request request, Response response) {
		response.setValue("Content-Type", "text/html");
		return "<head><title>SIDE Loader</title></head><body>" + "<h1>Upload Test Data</h1>"
				+ "<form action=\"CSVsave\" method=\"post\" enctype=\"multipart/form-data\">"
				+ "<input type=\"submit\" name=\"Submit\" value=\"Save\">" + "</form>" + "</body>";
	}

	protected String handleGetEvaluate(Request request, Response response, String header) {
		org.simpleframework.http.Path path = request.getPath();
		if (path.getSegments().length > 1) {
			String modelName = request.getPath().getPath(1).substring(1);
			response.setValue("Content-Type", "text/html");
			return "<head><title>SIDE Effects</title></head><body style=\"margin:16px\">" + header
					+ "<form action=\"/try/" + modelName + "\" method=\"post\" enctype=\"multipart/form-data\">"
					+ "<label for=\"sample\">Test the " + modelName + " model:</label><br>"
					+ "<textarea rows=\"5\" cols=\"40\" name=\"sample\" style=\"max-width:400px\"></textarea><br>"
					+ "<input type=\"submit\" name=\"Submit\" value=\"Evaluate Text\">" + "</form>" + "</body>";
		} else {
			response.setCode(400);
			return "Must provide a model name: localhost:8000/try/modelname";
		}
	}

	protected String handleEvaluate(Request request, Response response) throws IOException, FileNotFoundException {
		Part part = request.getPart("sample");
		String sample = part.getContent();

		String modelName = request.getPath().getPath(1).substring(1);
		String answer = checkModel(response, modelName);
		if (!answer.equalsIgnoreCase("OK")) {
			return answer;
		}

		PredictionResult prediction = predictors.get(modelName).predict(new DocumentList(sample));

		Map<String, Double> scores = prediction.getDistributionMapForInstance(0);

		String header = "";
		for (String label : scores.keySet()) {
			header += String.format("%s: %.1f%%<br>", label, 100 * scores.get(label));
		}

		return handleGetEvaluate(request, response,
				"<h3>" + header + "</h3><p style=\"max-width:400px\"><i>" + sample + "</i></p>");
	}

	/**
	 * @param request
	 * @param body
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	protected String handleUploadInputDocument(Request request, Response response)throws IOException, FileNotFoundException {
		Part part = request.getPart("inputfile");
		String file_Name = part.getFileName();
		String algo=request.getPart("algo").getContent();
		//copy the uploaded file into testdata folder
		final String destpath = Workbench.dataFolder.getAbsolutePath();
	    final Part filePart = request.getPart("inputfile");
	    final String filename = file_Name.substring(Math.max(file_Name.lastIndexOf("/"), file_Name.lastIndexOf("\\"))+1);
	    OutputStream out = null;
	    InputStream filecontent = null;
	    try {
	        out = new FileOutputStream(new File(destpath + File.separator
	                + filename));
	        filecontent = filePart.getInputStream();
	        int read = 0;
	        final byte[] bytes = new byte[1024];

	        while ((read = filecontent.read(bytes)) != -1) {
	            out.write(bytes, 0, read);
	        }
	    } catch (FileNotFoundException fne) {
	    	System.err.println("Error in prediction server");
	    } finally {
	        if (out != null) {
	            out.close();
	        }
	        if (filecontent != null) {
	            filecontent.close();
	        }
	    }
	    
	    Set<String> files = new HashSet<String>();
		files.add(file_Name);
		//creating a document list and setting all the required parameters for feature extraction
		DocumentList d = new DocumentList(files);
		String annot = "Complexity_level";
		if (d.getTextColumns().contains(annot))
		{
			d.setTextColumn(annot, false);
		}
		
		Type valueType = d.getValueType(annot);

		Map<String, Boolean> columns = new TreeMap<String, Boolean>();
		for (String s : d.allAnnotations().keySet())
		{
			if (!annot.equalsIgnoreCase(s)) columns.put(s, false);
		}
		for (String s : d.getTextColumns())
		{
			columns.put(s, true);
		}
		
		//removing text column from all annotations and adding it to textcolumns 
		d.setTextColumn("text", true);
		Workbench.update(RecipeManager.Stage.DOCUMENT_LIST);
		
	    logger.fine("Completed process of load file");
	    
		RecipeManager rp=Workbench.getRecipeManager();
		Recipe plan=Workbench.recipeManager.fetchDocumentListRecipe(d);
		
		//adding an extractor to recipe i.e Basic Features
		FeaturePlugin b = new BasicFeatures();
		FeaturePlugin c = new ColumnFeatures();
		Collection<FeaturePlugin> plugins = new HashSet<FeaturePlugin>();
		plugins.add(b);
		if(algo.equalsIgnoreCase("logistic"))
		{
			plugins.add(c);
		}
		
		Map<String, String> plugin_config_naive = new HashMap<String, String>(); 
		
		if(algo.equalsIgnoreCase("naive"))
		{
			plugin_config_naive.put("Bigrams","false");
			plugin_config_naive.put("Contains Non-Stopwords","false");
			plugin_config_naive.put("Count Occurences","false");
			plugin_config_naive.put("Ignore All-stopword N-Grams","false");
			plugin_config_naive.put("Include Punctuation","true");
			plugin_config_naive.put("Line Length","false");
			plugin_config_naive.put("Normalize N-Gram Counts","false");
			plugin_config_naive.put("POS Bigrams","false");
			plugin_config_naive.put("POS Trigrams","false");
			plugin_config_naive.put(" Skip Stopwords in N-Grams","false");
			plugin_config_naive.put("Stem N-Grams","false");
			plugin_config_naive.put("Track Feature Hit Location","true");
			plugin_config_naive.put("Trigrams","false");
			plugin_config_naive.put("Unigrams","true");
			plugin_config_naive.put("Word/POS Pairs","false");
			plan.addExtractor(b, plugin_config_naive);
		}
		else if (algo.equalsIgnoreCase("logistic"))
		{
			
			plugin_config_naive.put("Bigrams","true");
			plugin_config_naive.put("Contains Non-Stopwords","false");
			plugin_config_naive.put("Count Occurences","true");
			plugin_config_naive.put("Ignore All-stopword N-Grams","true");
			plugin_config_naive.put("Include Punctuation","true");
			plugin_config_naive.put("Line Length","true");
			plugin_config_naive.put("Normalize N-Gram Counts","true");
			plugin_config_naive.put("POS Bigrams","true");
			plugin_config_naive.put("POS Trigrams","true");
			plugin_config_naive.put(" Skip Stopwords in N-Grams","true");
			plugin_config_naive.put("Stem N-Grams","true");
			plugin_config_naive.put("Track Feature Hit Location","false");
			plugin_config_naive.put("Trigrams","true");
			plugin_config_naive.put("Unigrams","true");
			plugin_config_naive.put("Word/POS Pairs","true");
			plan.addExtractor(b, plugin_config_naive);
			
			Map<String, String> plugin_config_log = new HashMap<String, String>(); 
			plugin_config_log.put("Complexity_type", "NOMINAL");
			plan.addExtractor(c, plugin_config_log);
		}
		boolean halt=false;
		
		
		FeaturePlugin activeExtractor =  null;
		StatusUpdater update = new SwingUpdaterLabel();
	//checking the number of hits and generating feature table
		try
		{
			Collection<FeatureHit> hits = new HashSet<FeatureHit>();
			for (SIDEPlugin plug : plan.getExtractors().keySet())
			{
				if (!halt)
				{  
					activeExtractor = (FeaturePlugin) plug;
					hits.addAll(activeExtractor.extractFeatureHits(plan.getDocumentList(), plan.getExtractors().get(plug), update));
				}

			} 
			logger.fine("size of hits"+hits.size());
			if (!halt)
			{
				update.update("Building Feature Table");
				FeatureTable ft = new FeatureTable(plan.getDocumentList(), hits, 5 , annot , Type.NOMINAL);
				ft.setName(file_Name+"testFeatures");
				plan.setFeatureTable(ft);
				
			} 
		}
		catch (Exception e)
		{
			JOptionPane.showMessageDialog(null, "Couldn't finish the feature table.\nSee lightside_log for more details.\n"+e.getLocalizedMessage(),"Feature Failure",JOptionPane.ERROR_MESSAGE);
			System.err.println("Feature Extraction Failed");
		}
	
		Collection<Feature> features=plan.getFeatureTable().getSortedFeatures();
		logger.fine("Number of features extracted:"+ features.size());
		logger.fine("Created Feature Extraction!!");
		String s = handleBuildModel(plan,algo);
		logger.fine(s);
		//deleting the saved file
		File f= new File(destpath+"/"+filename);
		//f.delete();
		return s;
	}
	
	public String handleBuildModel(Recipe plan,String algo) {
		String accuracy="";

		SIDEPlugin[] learners = PluginManager.getSIDEPluginArrayByType("model_builder");
		
		if (algo.equalsIgnoreCase("naive"))
		{
			plan=plan.addLearnerToRecipe(plan,(LearningPlugin)learners[2] , learners[2].generateConfigurationSettings());
			
			WekaBayes wb= new WekaBayes();
			plan.setLearnerSettings(wb.generateConfigurationSettings());
		}
		else if (algo.equalsIgnoreCase("logistic"))
		{
			plan=plan.addLearnerToRecipe(plan,(LearningPlugin)learners[0] , learners[0].generateConfigurationSettings());
			WekaLogit wl = new WekaLogit();
			plan.setLearnerSettings(wl.generateConfigurationSettings());
		}
		else if (algo.equalsIgnoreCase("svm"))
		{
			plan=plan.addLearnerToRecipe(plan,(LearningPlugin)learners[1] , learners[1].generateConfigurationSettings());
			WekaSVM wl = new WekaSVM();
			plan.setLearnerSettings(wl.generateConfigurationSettings());
		}
		
		if (algo.equalsIgnoreCase("naive"))
		{
			BuildModelControl.updateValidationSetting("annotation", "E: Evidence");
		}
		else
		{
			//BuildModelControl.updateValidationSetting("annotation", "Complexity_type");		
		}
		
		BuildModelControl.updateValidationSetting("foldMethod", "AUTO");
		BuildModelControl.updateValidationSetting("numFolds", "10");
		BuildModelControl.updateValidationSetting("source", "RANDOM");
		BuildModelControl.updateValidationSetting("test","true");
		BuildModelControl.updateValidationSetting("testRecipe", plan);
		BuildModelControl.updateValidationSetting("testSet", plan.getDocumentList());
		BuildModelControl.updateValidationSetting("type", "CV");
		
		Map<String, Serializable> valSetting = BuildModelControl.getValidationSettings();
		
		for(String s:valSetting.keySet()) {
			logger.fine("BuildModelControl KEY: " + s + " value: " + valSetting.get(s));
		}
		
		try
		{
			FeatureTable current = plan.getTrainingTable();
			logger.fine("training table size:"+current.getSize());
			if (current != null)
			{
				TrainingResult results = null;
				if (results == null)
				{
					logger.fine("Training new model.");
					logger.fine("here!");
					logger.fine("size of learner settings:"+plan.getLearnerSettings().size());
					
					logger.fine(BuildModelControl.getValidationSettings().toString());
					logger.fine(plan.getWrappers().toString());
					logger.fine("learner:"+plan.getLearner());
					logger.fine("updater:"+BuildModelControl.getUpdater());
					results = plan.getLearner().train(current, plan.getLearnerSettings(), BuildModelControl.getValidationSettings(), plan.getWrappers(),
							BuildModelControl.getUpdater());
					logger.fine("trained size:"+results.getTrainingTable().getSize());
				}

				if (results != null)
				{
					logger.fine("Fetched Results successfully");
					plan.setTrainingResult(results);
					results.setName("BuiltModel");

					plan.setLearnerSettings(plan.getLearner().generateConfigurationSettings());
					plan.setValidationSettings(new TreeMap<String, Serializable>(BuildModelControl.getValidationSettings()));
					logger.fine("confusion matrix key set: "+results.getConfusionMatrix().keySet().size());
					logger.fine("Text Confusion Matrix: " + results.getTextConfusionMatrix());
					logger.fine("Evaluation: "+results.getEvaluationTable().getSize());
					Map<String, String> allKeys = new TreeMap<String, String>();

					//  Map<String, List<Double>> distributions = results.getDistributions();
					//  List<Double> values = new ArrayList<Double>();
					//  for(String s:distributions.keySet()){
					//  	logger.fine("Key: " + s);
					//  	values = distributions.get(s);
					//  	for(Double dis:values){
					//  		logger.fine("Key: " + s + " Prediction: " + dis);
					//  	}
					//  }

						Collection<ModelMetricPlugin> plugins = BuildModelControl.getModelEvaluationPlugins();
						for(ModelMetricPlugin plugin : plugins){
							Map<String, String> evaluations = plugin.evaluateModel(results, plugin.generateConfigurationSettings());
							results.cacheEvaluations(evaluations);
							for(String s : evaluations.keySet()){
								Vector<Object> row = new Vector<Object>();
								row.add(s);
								try{
									Double d = Double.parseDouble(evaluations.get(s));
									row.add(d);
								}catch(Exception e){
									row.add(evaluations.get(s));
								}
							}
							allKeys.putAll(evaluations);
						}			
						
					logger.fine("Model Evaluation Matrix");
					StringBuilder mapAsString = new StringBuilder("{");
					
					for (String key : allKeys.keySet()) {
						mapAsString.append(key + "=" + allKeys.get(key) + ", ");
						if (key.equalsIgnoreCase("Accuracy"))
						{
							accuracy=allKeys.get(key);
						}
					}

					mapAsString.append("}");

					logger.fine(mapAsString.toString());				
				}
			}
		}
		catch (Exception e)
		{
			logger.fine(e.getMessage());
			plan = null;
			
		}		

		Workbench.update(RecipeManager.Stage.TRAINED_MODEL);
		Workbench.getRecipeManager().addRecipe(plan);  
		return accuracy;
	}
	
	protected String handleTestPredict(Request request, Response response) throws IOException, FileNotFoundException {
		//String accuracy="";
		String annot = "all_type";
		String jsonStr = "";

		ObjectMapper objectMapper = new ObjectMapper();
		ResponseJson rj = classifyPrediction(request, response, annot);	

		if(!rj.getPredicted().equalsIgnoreCase("L-IS")){
			ResponseJson rsj  = classifyPrediction(request, response, rj.getPredicted());

			ResponseJson rsjXT = null;
			if(rsj.getPredicted().equalsIgnoreCase("L-X")||rsj.getPredicted().equalsIgnoreCase("L-T"))
				rsjXT = classifyPrediction(request, response, rsj.getPredicted());	
			
			logger.fine("--- Start Prediction of all_type---");
		
			logger.fine("Accuracy:   " + rj.getAccuracy());		
			logger.fine("Level: 		" + rj.getLevel());	
			logger.fine("Prediction: " + rj.getPredicted());
			
			logger.fine("--- End	  Prediction of all_type---");	
			
			logger.fine("--- Start Prediction of " + rsj.getPredicted() + "---");
			
			logger.fine("Accuracy:   " + rsj.getAccuracy());		
			logger.fine("Level: 		" + rsj.getLevel());	
			logger.fine("Prediction: " + rsj.getPredicted());
			
			logger.fine("--- End	  Prediction of " + rsj.getPredicted() + "---");
			
			if(rsjXT==null) {
				jsonStr = objectMapper.writeValueAsString(rsj);
				writeToDB(rsj);
			}
			else{
				jsonStr = objectMapper.writeValueAsString(rsjXT);
				
				logger.fine("--- Start Prediction of " + rsjXT.getPredicted() + "---");
				
				logger.fine("Accuracy:   " + rsjXT.getAccuracy());		
				logger.fine("Level: 		" + rsjXT.getLevel());	
				logger.fine("Prediction: " + rsjXT.getPredicted());
				
				logger.fine("--- End	  Prediction of " + rsjXT.getPredicted() + "---");
				writeToDB(rsjXT);
			}
		}else{
			//Insufficient Data
			jsonStr = objectMapper.writeValueAsString(rj);
			writeToDB(rj);
		}
		logger.info("json: " + jsonStr);
		logger.fine("json: " + jsonStr);
		return jsonStr;
	}
	
	private ResponseJson classifyPrediction(Request request, Response response, String annot) throws IOException, FileNotFoundException {
		String algo="logistic";
		boolean type = true;
		String jsonStr = "";
		String train_file = "";
		String predictedLabel = ""; //request.getPart("prediction_column").getContent();
		ResponseJson rJson = null;
		
		// idea statements
		if(annot.equalsIgnoreCase("L-I"))
		{
			train_file="Train_KF2.csv";
			predictedLabel = "Complexity_level";
			annot = "Complexity_level";
		}
		// questions
		else if(annot.equalsIgnoreCase("L-Q"))
		{
			train_file="Train_question.csv";
			predictedLabel = "question_type";
			annot="question_type";
		}
		// all types
		else if(annot.equalsIgnoreCase("all_type"))
		{
			//0.6288659793814433
			train_file="Train_all_types.csv";
			predictedLabel = "all_type";
			annot="all_type";
		}
		// resources
		else if(annot.equalsIgnoreCase("L-R"))
		{
			train_file="Train_resource.csv";
			predictedLabel = "resource_type";
			annot="resource_type";
		}
		// explanations
		else if(annot.equalsIgnoreCase("L-X"))
		{
			train_file="Train_KF_X.csv";
			predictedLabel = "Complexity_level";
			annot = "Complexity_level";
		}
		// facts
		else if(annot.equalsIgnoreCase("L-T"))
		{
			train_file="Train_KF_T.csv";
			predictedLabel = "Complexity_level";
			annot = "Complexity_level";
		}

		logger.fine("annot: "+annot);

		logger.fine("predictedLabel: "+predictedLabel);

		logger.fine("algo: "+algo);
		final String destpath = Workbench.dataFolder.getAbsolutePath();
		File f= new File(destpath+"/"+train_file);
		logger.fine("Training file dir: "+destpath+"/"+train_file);
		Set<String> files = new HashSet<String>();
			files.add(train_file);
			//creating a document list and setting all the required parameters for feature extraction
			DocumentList d = new DocumentList(files);
			if (d.getTextColumns().contains(annot))
			{
				d.setTextColumn(annot, false);
			}
			
			Type valueType = d.getValueType(annot);

			Map<String, Boolean> columns = new TreeMap<String, Boolean>();
			for (String s : d.allAnnotations().keySet())
			{
				if (!annot.equalsIgnoreCase(s)) columns.put(s, false);
			}
			for (String s : d.getTextColumns())
			{
				columns.put(s, true);
			}
			//removing text column from all annotations and adding it to textcolumns 
			d.setTextColumn("text", true);
			Workbench.update(RecipeManager.Stage.DOCUMENT_LIST);
			
		    logger.fine("Completed process of load file");
		    
			RecipeManager rp=Workbench.getRecipeManager();
			Recipe plan = Workbench.recipeManager.fetchDocumentListRecipe(d);
			//adding an extractor to recipe i.e Basic Features
			FeaturePlugin b = new BasicFeatures();
			FeaturePlugin c = new ColumnFeatures();
			Collection<FeaturePlugin> plugins = new HashSet<FeaturePlugin>();
			plugins.add(b);
			if(algo.equalsIgnoreCase("logistic") || algo.equalsIgnoreCase("svm"))
			{
				plugins.add(c);
			}
			
			
			ObjectMapper mapper = new ObjectMapper();	 	 
			Map<String, String> plugin_config_naive =new HashMap<String, String>();
			Map<String, Object> map1 ;
	        /**
	         * Read JSON from a file into a Map
	         */
	        try {
	        	
	        	if(algo.equalsIgnoreCase("naive"))
	        	{
	        		logger.fine("path to check json:"+destpath+"/Question.json");
	        		map1 = mapper.readValue(new File(destpath+
		                    "/Question.json"), new TypeReference<Map<String, Object>>() {
		            }); 
	        		
	        		for(String w:map1.keySet())
	        		{
	        			plugin_config_naive.put(w, map1.get(w).toString());
	        		}
	        		plan.addExtractor(b, plugin_config_naive);
	        	}
	        	else if (algo.equalsIgnoreCase("logistic"))
				{
	        		map1 = mapper.readValue(new File(destpath+
		                    "/Complexity.json"), new TypeReference<Map<String, Object>>() {
		            }); 
	        		for(String w:map1.keySet())
	        		{
	        			plugin_config_naive.put(w, map1.get(w).toString());
	        		}
	        		plan.addExtractor(b, plugin_config_naive);
					Map<String, String> plugin_config_log = new HashMap<String, String>(); 
					// increase accuracy
					//if (type&&annot.equalsIgnoreCase("Complexity_level"))
					//	plugin_config_log.put("Complexity_type", "NOMINAL");
					//plugin_config_log.put("E: Evidence", "NOMINAL");
					plan.addExtractor(c, plugin_config_log);
				}
	        	else if (algo.equalsIgnoreCase("svm"))
				{
	        		map1 = mapper.readValue(new File(destpath+
		                    "/Complexity.json"), new TypeReference<Map<String, Object>>() {
		            }); 
	        		for(String w:map1.keySet())
	        		{
	        			plugin_config_naive.put(w, map1.get(w).toString());
	        		}
	        		plan.addExtractor(b, plugin_config_naive);
					Map<String, String> plugin_config_log = new HashMap<String, String>(); 
					plan.addExtractor(c, plugin_config_log);
				}
	 
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	        
			boolean halt=false;
			
			
			FeaturePlugin activeExtractor =  null;
			StatusUpdater update = new SwingUpdaterLabel();
		//checking the number of hits and generating feature table
			try
			{
				Collection<FeatureHit> hits = new HashSet<FeatureHit>();
				for (SIDEPlugin plug : plan.getExtractors().keySet())
				{
					if (!halt)
					{  
						activeExtractor = (FeaturePlugin) plug;
						hits.addAll(activeExtractor.extractFeatureHits(plan.getDocumentList(), plan.getExtractors().get(plug), update));
					}

				} 
				logger.fine("size of hits"+hits.size());
				if (!halt)
				{
					update.update("Building Feature Table");
					FeatureTable ft = new FeatureTable(plan.getDocumentList(), hits, 5 , annot , Type.NOMINAL);
					ft.setName(train_file);
					plan.setFeatureTable(ft);
					
				} 
			}
			catch (Exception e)
			{
				JOptionPane.showMessageDialog(null, "Couldn't finish the feature table.\nSee lightside_log for more details.\n"+e.getLocalizedMessage(),"Feature Failure",JOptionPane.ERROR_MESSAGE);
				System.err.println("Feature Extraction Failed");
			}
		
			Collection<Feature> features=plan.getFeatureTable().getSortedFeatures();
			logger.fine("Number of features extracted:"+ features.size());
			logger.fine("Created Feature Extraction!!");
			jsonStr = handleBuildModel(plan,algo);
			logger.fine(jsonStr);
			
			//predict the testing data
			String answer="";
			Collection<Recipe> recipelist=Workbench.getRecipeManager().getRecipeCollectionByType(RecipeManager.Stage.TRAINED_MODEL);
			
			List<Recipe> rplist=new ArrayList<Recipe>(recipelist);
			Recipe trainedModel= rplist.get(rplist.size()-1);
			boolean useEvaluation=false;
			boolean showDists=true;
			boolean overwrite=false;
			DocumentList originalDocs;
			DocumentList newDocs = null;
			Exception ex = null;
			String name="PredictedTestData";
			
			final Query query = request.getQuery();
			String requestID = "", jsonString = "", typeString = "";
			String currentTimeStamp = new SimpleDateFormat("MM-dd-yyyy").format(new Date());
			String requestorName = "KF";
			
			try
			{		
				if( query != null && query.get("jsonString") != null){		
					
					requestID = (String) query.get("requestID");
					jsonString = preprocessRawString((String) query.get("jsonString"));
					requestorName = (String) query.get("requestorName");	

					logger.fine("requestID: " + requestID);
					logger.fine("JSON: " + jsonString);	
					//jsonString = request.getPart("jsonStr").getContent();
					// if(query.get("typeString")==null)
					// 	typeString = "";
					// else				
					// 	typeString = (String) query.get("typeString");	
					//logger.fine("query Complexity_type: " + typeString);
				}
	
				if( request.getPart("requestID") != null ){
					requestID = request.getPart("requestID").getContent();
					jsonString = preprocessRawString(request.getPart("jsonString").getContent());
					requestorName = request.getPart("requestorName").getContent();	

					logger.fine("requestID: " + requestID);	
					logger.fine("JSON: " + jsonString);				
					// typeString = request.getPart("Complexity_type").getContent();
					// logger.fine("Complexity_type: " + typeString);
				}
				logger.fine("annot: " + annot);
				logger.fine("recipelist size: " + recipelist.size());

				//creating a document list and setting all the required parameters for feature extraction
				//originalDocs = new DocumentList(testfiles);
				
				// json string must either be a url
				// or sentence has >= three words
				if( jsonString.equalsIgnoreCase("    i need to understand  --") 
				|| jsonString.equalsIgnoreCase("    my theory  - test-") 
				|| jsonString.equalsIgnoreCase("    my theory  --") 
				|| jsonString.equalsIgnoreCase("    putting our knowledge together  - i know that the anthills-") 
				|| jsonString.equalsIgnoreCase("    this theory cannot explain  - why animals-") 
				|| jsonString.equalsIgnoreCase("I agree with you too") 
				|| jsonString.equalsIgnoreCase("i dont know") 
				|| jsonString.equalsIgnoreCase("so dark") 
				|| jsonString.equalsIgnoreCase("testing this")){
					// Insufficient data. Please write more.
					rJson = new ResponseJson(requestID, "L-IS", "", jsonStr, "", "", "", "");
				} else //if( 
				//	|| (jsonString.contains(" ")&&jsonString.split(" ").length>2)
				//	(!jsonString.contains(" ")&&jsonString.contains("http")) ) 
				{
					originalDocs = new DocumentList(annot, jsonString, typeString);
					
					originalDocs.setTextColumn("text", true);

					Predictor predictor = new Predictor(trainedModel, name);
					// set "show distribution" to be true
					newDocs = predictor.predict(originalDocs, name, showDists, overwrite);
					
					String[] a=newDocs.getAnnotationNames();
					Map<String, List<String>> allAnnotations = newDocs.allAnnotations();

					// get distribution
					String key1="", key2 = "";
					for(String s:allAnnotations.keySet()){
						// if(allAnnotations.get(s)!=null)
						// if(s.equalsIgnoreCase("all_type"))
						// 	continue;
						logger.fine("Predicted Label: " + s + " Predicted Label likelihood: " + allAnnotations.get(s).get(0));
						if(!s.equalsIgnoreCase(predictedLabel)&&!s.equalsIgnoreCase("PredictedTestData")&&key1.equalsIgnoreCase(""))
							key1 = s;

						if(!s.equalsIgnoreCase(predictedLabel)&&!s.equalsIgnoreCase("PredictedTestData")
							&&!key1.equalsIgnoreCase("")&&!key1.equalsIgnoreCase(s)&&key2.equalsIgnoreCase(""))
							key2 = s;
					}

					List<String> level = allAnnotations.get(predictedLabel);
					List<String> predicted = allAnnotations.get("PredictedTestData");
					List<String> key1Str = allAnnotations.get(key1);
					List<String> key2Str = allAnnotations.get(key2);

					// manual classification
					if(!predicted.get(0).contains("L-R") && jsonString.contains("http"))
						predicted.set(0, "L-R");

					// manual question classification
					// filter "Train_question.csv (question_type_prediction).csv" by "PredictedTestData_L-Q" column
					// using threshold 0.35
					String predictedLQ = "PredictedTestData_L-Q";
					if(allAnnotations.keySet().contains(predictedLQ)) {
						double lq = Double.parseDouble(allAnnotations.get(predictedLQ).get(0));
						if(lq > 0.35) {
							predicted.set(0, "L-Q");
						}
					}
					
					// filter "ComplexityPrediction -train resource.csv" by "PredictedTestData_L-R" column
					// using threshold 0.24
					String predictedLR = "PredictedTestData_L-R";
					if(allAnnotations.keySet().contains(predictedLR)) {
						double lq = Double.parseDouble(allAnnotations.get(predictedLR).get(0));
						if(lq > 0.24) {
							predicted.set(0, "L-R");
						}
					}

					logger.fine("Predicted Label1: "+ key1 + " Predicted Label1: "+key2);
					logger.fine("Predicted Label1 likelihood: "+allAnnotations.get(key1).get(0) + " Predicted Label2 likelihood: "+allAnnotations.get(key2).get(0));
					
					rJson = new ResponseJson(requestID, predicted.get(0).toUpperCase(), level.get(0), jsonStr, key1, key2, key1Str.get(0), key2Str.get(0));
					
				}
				// insufficient data
				// two criteria: # of words < 3
				rJson.setFeedbackTextByPredicted(rJson.getPredicted());
				rJson.setRequestTimestamp(currentTimeStamp);
				rJson.setRequesterName(requestorName);
				rJson.setNoteText(jsonString);
				rJson.setNoteID(requestID); // noteID???
			}
			catch(Exception e)
			{
				logger.fine(e.getMessage());
				e.printStackTrace();
				ex = e;				
			}
			
			// if(newDocs.getSize()!=0)
			// {
			// 	answer="Success";
			// }
			
			Workbench.getRecipeManager().deleteRecipe(trainedModel);
			// trainedModel.setDocumentList(newDocs);
			// Workbench.getRecipeManager().addRecipe(trainedModel);
			
			return rJson;
	}

	protected String getFeedbackText(String predicted){
		String feedbackText = "";
		
		if("L-RF".equalsIgnoreCase(predicted)){
			feedbackText = "Thanks for sharing this resource. \n" + 
					"-Can you say more? Why is this resource useful for our knowledge building?";
		}else if("L-RS".equalsIgnoreCase(predicted)){
			feedbackText = "Thank you for sharing this piece of our puzzle. Keep thinking and posting!";
		}else if("L-QF".equalsIgnoreCase(predicted)){
			feedbackText = "Good start with your wondering!\n" + 
					"-Can you say more about your question or thought?";
		}else if("L-QS".equalsIgnoreCase(predicted)){
			feedbackText = "Good job! Keep researching and sharing your ideas!";
		}else if("L-F".equalsIgnoreCase(predicted)){
			feedbackText = "You are sharing some interesting information.  \n" + 
					"-Can you add more details or say more about what this means?";
		}else if("L-EF".equalsIgnoreCase(predicted)){
			feedbackText = "Thanks for sharing this interesting information.  \n" + 
					"-Can you explain how this exactly works, and why?";
		}else if("L-E".equalsIgnoreCase(predicted)){
			feedbackText = "This looks like an interesting idea. \n" + 
					"-	Can you say more?";
		}else if("L-EE".equalsIgnoreCase(predicted)){
			feedbackText = "Good job!  This looks like a great note! Encourage your peers to read it. Think about what you need to further research.";
		}
		
		return feedbackText;
	}
	
	private void writeToDB(ResponseJson rj) {
		
	}

	protected String preprocessRawString(String jsonStr) throws IOException {
		String rtn = "";
		final String destpath = Workbench.dataFolder.getAbsolutePath();
		// PorterStemmer stemmer = new PorterStemmer();

		
		//.toLowerCase()
		// remove punctuations except "-" and numbers
		ArrayList<String> allWords = Stream.of(jsonStr.replaceAll("[^a-zA-Z- ]", "").split(" ")).collect(Collectors.toCollection(ArrayList<String>::new));
		
		// List<String> stopwords = Files.readAllLines(Paths.get(destpath + File.separator + "nlp_en_stop_words.txt"));
		// allWords.removeAll(stopwords);
		
		// stemming
		// int i = 0;
		// for(String s:allWords) {
		// 	allWords.set(i, stemmer.stem(s));
		// 	i++;
		// }

		rtn = allWords.stream().collect(Collectors.joining(" ")).toLowerCase();

		return rtn;
	}
	
	protected String handleUpload(Request request, Response response) throws IOException, FileNotFoundException {
		Part part = request.getPart("model");
		String nick = request.getPart("modelNick").getContent();
		String path = part.getFileName();
		// TODO: use threaded tasks.

		if (part != null && part.getFileName() != null) {
			if (path != null && !path.isEmpty() && (nick == null || nick.isEmpty())) {
				nick = path.replace(".model.side", "");
			}

			if (nick.isEmpty() || path.equalsIgnoreCase("null")) {
				response.setCode(400);
				return "upload requires both serialized model file and model name.";
			}
			nick = nick.replaceAll("/", "-");
			nick = nick.replaceAll("\\s", "-");

			if (predictors.containsKey(nick)) {
				response.setCode(409);
				return "nickname '" + nick + "'is already in use for a model at " + predictors.get(nick).getModelPath();
			}

			File f = new File("saved/" + nick + ".model.side");
			if (f.exists()) {
				response.setCode(409);
				return nick + " already exists on this server.";
			} else {
				// TODO: authentication?
				logger.fine(f.getAbsolutePath());
				FileChannel fo = new FileOutputStream(f).getChannel();
				ReadableByteChannel po = Channels.newChannel(part.getInputStream());
				long transferred = fo.transferFrom(po, 0, Integer.MAX_VALUE);
				logger.fine("wrote " + transferred + " bytes.");

				Long when = System.currentTimeMillis();
				boolean attached = attachModel(nick, f.getAbsolutePath());
				logger.fine("Attach model took " + (System.currentTimeMillis() - when) / 1000.0 + " seconds");

				if (attached)
					return "received " + path + ": " + transferred + " bytes.\nModel attached as /predict/" + nick + "";
				else {
					f.delete();
					response.setCode(418);
					return "could not attach model '" + path
							+ "' -- was it trained on the latest version of LightSide?";
				}
			}
		} else {
			response.setCode(400);
			return "No model file received.";
		}
	}

	public PredictionServer(int size) {
		this.executor = Executors.newFixedThreadPool(size);
	}
	
	protected String handlePredictTest(Request request, Response response) throws IOException {
		// TODO: use threaded tasks.
		String answer = "";
		Collection<Recipe> recipelist=Workbench.getRecipeManager().getRecipeCollectionByType(RecipeManager.Stage.TRAINED_MODEL);
		
		List<Recipe> rp=new ArrayList<Recipe>(recipelist);
		Recipe trainedModel= rp.get(0);
		boolean useEvaluation=false;
		boolean showDists=false;
		boolean overwrite=false;
		DocumentList originalDocs;
		DocumentList newDocs = null;
		Exception ex = null;
		String name="PredictedTestData";
		try
		{
				originalDocs = trainedModel.getDocumentList();

				Predictor predictor = new Predictor(trainedModel, name);
				newDocs = predictor.predict(originalDocs, name, showDists, overwrite);
				String[] a=newDocs.getAnnotationNames();
				
		}
		catch(Exception e)
		{
			ex = e;
			
		}
		if(newDocs.getSize()!=0)
		{
			answer="Success";
		}
		
		Workbench.getRecipeManager().deleteRecipe(trainedModel);
		trainedModel.setDocumentList(newDocs);
		Workbench.getRecipeManager().addRecipe(trainedModel);
		
		return answer;
	}
	
	protected String handleCSVSave(Request request, Response response) throws IOException {
		
		Collection<Recipe> recipelist=Workbench.getRecipeManager().getRecipeCollectionByType(RecipeManager.Stage.TRAINED_MODEL);
		List<Recipe> rp=new ArrayList<Recipe>(recipelist);
		DocumentListTableModel model = new DocumentListTableModel(null);
		Recipe recipe = rp.get(0);
		model.setDocumentList(recipe.getDocumentList());
		if(recipe != null)
		{
			CSVExporter.exportToCSV(model, recipe.getDocumentList().getName()); 
		}
		logger.fine("Saved CSV file!");
		return "Success";
		
	}

	protected String handlePredict(Request request, Response response) throws IOException {
		// TODO: use threaded tasks.
		String answer = "";
		String model = "";

		try {

			Query query = request.getQuery();

			List<String> instances = query.getAll("q");

			model = request.getPath().getPath(1).substring(1);

			Long when = System.currentTimeMillis();
			checkModel(response, model);
			logger.fine("Check model took " + (System.currentTimeMillis() - when) / 1000.0 + " seconds");

			logger.fine("using model " + model + " on " + instances.size() + " instances...");
			for (Comparable label : predictors.get(model).predict(instances)) {
				answer += label + " ";
			}
			answer = answer.trim();

			if (answer.isEmpty())
				response.setCode(500);

			return answer;

		} catch (Exception e) {
			e.printStackTrace();
			response.setCode(400);
			return "could not handle request: " + request.getTarget()
					+ "\n(urls should be of the form /predict/model/?q=instance...)";
		}
	}

	protected String checkModel(Response response, String model) {
		if (!predictors.containsKey(model)) {
			// attempt to attach a local model
			File f = new File("saved/" + model + ".model.side");
			if (!f.exists())
				f = new File("saved/" + model + ".predict");
			if (!f.exists())
				f = new File("saved/" + model);

			if (f.exists()) {
				boolean attached = attachModel(model, f.getAbsolutePath());
				if (!attached) {
					response.setCode(418);
					return "could not load existing model for '" + model
							+ "' -- was it trained on the latest version of LightSide?";
				}
			} else {
				response.setCode(404);
				return "no model available named " + model;
			}
		}
		return "OK";
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			printUsage();
		}

		// initSIDE();
		int port = 9098;

		int start = 0;
		if (args.length > 0 && !args[0].contains(":")) {
			try {
				start = 1;
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				printUsage();
			}
		}

		for (int i = start; i < args.length; i++) {

			String[] modelConfig = args[i].split(":");
			String modelNick = modelConfig[0];
			String modelPath = modelConfig[1];

			attachModel(modelNick, modelPath);
		}

		if (predictors.isEmpty()) {
			logger.fine("Warning: no models attached yet. Use http://localhost:" + port + "/uploadinput");
		}

		serve(port, 100);

	}

	/**
	 * 
	 */
	protected static void printUsage() {
		logger.fine("usage: side_server.sh [port] model_nickname:path/to/model.side ...");
	}

	/**
	 * @param modelPath
	 * @param annotation
	 * @param annotation2
	 * @return
	 */
	protected static boolean attachModel(String nick, String modelPath) {
		try {
			logger.fine("attaching " + modelPath + " as " + nick);
			Predictor predict = new Predictor(modelPath, "class");
			predictors.put(nick, predict);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

}  
