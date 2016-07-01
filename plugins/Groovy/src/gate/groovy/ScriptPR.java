/*
 * Copyright (c) 2010, The University of Sheffield.
 *
 * This file is part of the GATE/Groovy integration layer, and is free software,
 * released under the terms of the GNU Lesser General Public Licence, version
 * 2.1 (or any later version). A copy of this licence is provided in the file
 * LICENCE in the distribution.
 *
 * Groovy is developed by The Codehaus, details are available from
 * http://groovy.codehaus.org
 */

package gate.groovy;

import gate.AnnotationSet;
import gate.Controller;
import gate.Corpus;
import gate.CorpusController;
import gate.FeatureMap;
import gate.Gate;
import gate.Factory;
import gate.ProcessingResource;
import gate.Resource;
import gate.Document;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ControllerAwarePR;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.BomStrippingInputStreamReader;
import gate.util.GateClassLoader;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.MetaMethod;
import groovy.lang.Script;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.List;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.InvokerInvocationException;

/**
 * Groovy Script PR.
 *
 * @author Angus Roberts, Ian Roberts
 */
@CreoleResource(name = "Groovy scripting PR", comment = "Runs a Groovy script as a processing resource", helpURL = "http://gate.ac.uk/userguide/sec:api:groovy", icon = "/gate/groovy/script-pr")
public class ScriptPR extends AbstractLanguageAnalyser implements
                                                      ProcessingResource,
                                                      ControllerAwarePR {

  private static final long serialVersionUID = 7563063357390091727L;

  /**
   * Groovy script file
   */
  private URL scriptURL;

  /**
   * Parameters passed to the Groovy script
   */
  private FeatureMap scriptParams;

  /**
   * The compiled Groovy script.
   */
  private Script groovyScript;

  /**
   * the source of the loaded groovy script for use in the VR
   */
  private String groovySrc;

  /**
   * The character encoding of the script file.
   */
  private String encoding;

  /**
   * Name of the output annotation set
   */
  private String outputASName;

  /**
   * Name of the input annotation set
   */
  private String inputASName;
  
  private GateClassLoader classloader = null;

  /** Initialise this resource, and return it. */
  public Resource init() throws ResourceInstantiationException {

    if(scriptURL == null)
      throw new ResourceInstantiationException(
              "You must specify a Groovy script to load");

    //if we are being re-initialized then forget the class loader we used last
    if (classloader != null) Gate.getClassLoader().forgetClassLoader(classloader);
    
    //create a disposable classloader for the groovy shell to use as its parent
    classloader = Gate.getClassLoader().getDisposableClassLoader(scriptURL.toExternalForm()+System.currentTimeMillis(), ScriptPR.class.getClassLoader(), true);
    
    // Create the shell, with the GateClassLoader as its parent (so the script
    // will have access to plugin classes)
    GroovyShell groovyShell = new GroovyShell(classloader);
    StringBuilder scriptText = new StringBuilder();

    char[] buf = new char[4096];
    int charsRead = 0;
    try {
      Reader reader =
              new BomStrippingInputStreamReader(scriptURL.openStream(),
                      encoding);
      while((charsRead = reader.read(buf)) >= 0) {
        scriptText.append(buf, 0, charsRead);
      }
      reader.close();

      groovySrc = scriptText.toString();

      // append a load of standard imports to the end of the script. We put them
      // at the end rather than the beginning because (in a script) imports work
      // anywhere, and putting them at the end means we don't mess up line
      // numbers in any compilation error messages.
      scriptText.append("\n\n\n");
      scriptText.append(GroovySupport.STANDARD_IMPORTS);

      // determine the file name of the script
      String scriptFileName = scriptURL.toString();
      scriptFileName =
              scriptFileName.substring(scriptFileName.lastIndexOf('/'));

      groovyScript = groovyShell.parse(scriptText.toString(), scriptFileName);
    } catch(IOException ioe) {
      throw new ResourceInstantiationException("Error loading Groovy script "
              + "from URL " + scriptURL, ioe);
    }

    catch(CompilationFailedException e) {
      throw new ResourceInstantiationException("Script compilation failed", e);
    } finally {
      fireProcessFinished();
    }

    return this;
  }

  public void reInit() throws ResourceInstantiationException {
    init();
  }

  @Override
  public void cleanup() {
    
    //do the normal clean up first
    super.cleanup();
    
    //make sure we clean up properly before being destroyed
    if (classloader != null) Gate.getClassLoader().forgetClassLoader(classloader);
  }
  
  // ControllerAwarePR implementation

  public void controllerExecutionStarted(Controller c)
          throws ExecutionException {
    // ensure scriptParams are available to the callback
    if(scriptParams == null) { scriptParams = Factory.newFeatureMap(); }
    groovyScript.getBinding().setVariable("scriptParams", scriptParams);
    groovyScript.getBinding().setVariable("controller",c);
    callControllerAwareMethod("beforeCorpus", c);
  }

  public void controllerExecutionFinished(Controller c)
          throws ExecutionException {
    callControllerAwareMethod("afterCorpus", c);
  }

  public void controllerExecutionAborted(Controller c, Throwable t)
          throws ExecutionException {
    callControllerAwareMethod("aborted", c);
  }

  /**
   * Check whether the script declares a method with the given name that takes a
   * corpus parameter, and if so, call it passing the corpus from the given
   * controller. If the controller is not a CorpusController, do nothing.
   *
   * @throws ExecutionException
   *           if the script method throws an ExecutionException we re-throw it
   */
  protected void callControllerAwareMethod(String methodName, Controller c)
          throws ExecutionException {
    if(!(c instanceof CorpusController)) { return; }
    List<MetaMethod> metaMethods =
            groovyScript.getMetaClass().respondsTo(groovyScript, methodName,
                    new Class[]{gate.Corpus.class});
    if(!metaMethods.isEmpty()) {
      try {
        metaMethods.get(0).invoke(groovyScript,
                new Corpus[]{((CorpusController)c).getCorpus()});
      } catch(InvokerInvocationException iie) {
        if(iie.getCause() instanceof ExecutionException) {
          throw (ExecutionException)iie.getCause();
        } else if(iie.getCause() instanceof RuntimeException) {
          throw (RuntimeException)iie.getCause();
        } else if(iie.getCause() instanceof Error) {
          throw (Error)iie.getCause();
        } else {
          throw iie;
        }
      }
    }
  }

  /**
   * Execute method. Runs the groovy script, first passing a set of bindings
   * including the document, the input AnnotationSet and the output
   * AnnotationSet
   */
  public void execute() throws ExecutionException {

    AnnotationSet outputAS = null;
    AnnotationSet inputAS = null;
    if(document != null) {
      if(outputASName == null || outputASName.trim().length() == 0)
        outputAS = document.getAnnotations();
      else 
        outputAS = document.getAnnotations(outputASName);
      if(inputASName == null || inputASName.trim().length() == 0)
        inputAS = document.getAnnotations();
      else 
        inputAS = document.getAnnotations(inputASName);
      fireStatusChanged("Groovy script PR running on " + document.getSourceUrl());
    } else {
      fireStatusChanged("Groovy script PR running");
    }      
    fireProgressChanged(0);
    
    Binding binding = groovyScript.getBinding();
    
    try {
      // Create the variable bindings      
      binding.setVariable("doc", document);
      binding.setVariable("corpus", corpus);
      if(document != null) {
        binding.setVariable("content", document.getContent().toString());
      } else {
        binding.setVariable("content", null);
      }
      binding.setVariable("inputAS", inputAS);
      binding.setVariable("outputAS", outputAS);
  
      // these should be deprecated, really, they're no longer necessary with the
      // imports
      binding.setVariable("gate", Gate.class);
      binding.setVariable("factory", gate.Factory.class);
  
      // The FeatureMap is passed in its entirety, making the keys available in
      // a bean-like way. So in a map with k=v, the script can say
      // assert scriptParams.k == v
      binding.setVariable("scriptParams", scriptParams);
  
      // Run the script engine    
      groovyScript.run();
    } catch(RuntimeException re) {
      throw new ExecutionException("Problem running Groovy script", re);
    }
    finally {
      binding.setVariable("doc", null);
      binding.setVariable("corpus", null);
      binding.setVariable("content", null);
      binding.setVariable("inputAS", null);
      binding.setVariable("outputAS", null);
      
      // TODO not sure if we should nullify scriptParams as well, but I'm
      // thinking it's fairly safe not to as they shouldn't be document/corpus
      // based
    }

    // We've done
    fireProgressChanged(100);
    fireProcessFinished();
    fireStatusChanged("Groovy script PR finished");
  }

  /**
   * gets name of the output annotation set
   *
   * @return
   */
  public String getOutputASName() {
    return outputASName;
  }

  /**
   * sets name of the output annotaiton set
   *
   * @param outputAS
   */
  @Optional
  @RunTime
  @CreoleParameter
  public void setOutputASName(String outputAS) {
    this.outputASName = outputAS;
  }

  /**
   * gets name of the input annotation set
   *
   * @return
   */
  public String getInputASName() {
    return inputASName;
  }

  /**
   * sets name of the input annotaiton set
   *
   * @param inputAS
   */
  @Optional
  @RunTime
  @CreoleParameter
  public void setInputASName(String inputAS) {
    this.inputASName = inputAS;
  }

  /**
   * gets URL of the Groovy script
   *
   * @return
   */
  public URL getScriptURL() {
    return scriptURL;
  }

  /**
   * sets File of the Groovy script
   *
   * @param script
   */
  @CreoleParameter(comment = "Location of the Groovy script that will be "
          + "run for each document")
  public void setScriptURL(URL script) {
    this.scriptURL = script;
  }

  /**
   * Get the character encoding used to load the script.
   */
  public String getEncoding() {
    return encoding;
  }

  /**
   * Set the character encoding used to load the script.
   */
  @CreoleParameter(defaultValue = "UTF-8", comment = "Character encoding used "
          + "to read the script")
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  /**
   * Get Map of parameters for the Groovy script
   *
   * @return
   */
  public FeatureMap getScriptParams() {
    return scriptParams;
  }

  /**
   * Set Map of parameters for the Groovy script
   *
   * @return
   */
  @Optional
  @RunTime
  @CreoleParameter(comment = "Optional additional parameters to pass to the "
          + "script.")
  public void setScriptParams(FeatureMap params) {
    this.scriptParams = params;
  }

  /**
   * Return the source of the loaded groovy script
   *
   * @return the source of the loaded groovy script
   */
  public String getGroovySrc() {
    return groovySrc;
  }
  
  @Override
  @Optional
  @RunTime
  @CreoleParameter(comment = "The document to process")
  public void setDocument(Document doc) {
    document = doc;
  } 

}
