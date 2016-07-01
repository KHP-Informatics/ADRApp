/*
 * Copyright (c) 2009-2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * Licensed under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */
package gate.creole.measurements;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.LanguageAnalyser;
import gate.Resource;
import gate.Utils;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.creole.metadata.Sharable;
import gate.event.ProgressListener;

import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * A GATE PR which annotates and normalizes measurements. Each measurement is
 * also normalized back to SI units which allows measurements specified using
 * different units to be compared. For example 30cm and 300mm are both
 * normalized to 0.3m. The parsing of measurements is based upon a modified
 * version of the <a href="http://units-in-java.sourceforge.net/">Java port</a>
 * of the <a href="http://www.gnu.org/software/units/">GNU Units</a> package.
 * 
 * @see <a href="http://gate.ac.uk/userguide/sec:misc-creole:measurements">The
 *      GATE User Guide</a>
 * @author Mark A. Greenwood
 */
@CreoleResource(name = "Measurement Tagger", comment = "A measurement tagger based upon GNU Units", icon = "measurements.png")
public class MeasurementsTagger extends AbstractLanguageAnalyser implements
                                                                ProgressListener {
  private static final long serialVersionUID = -12362162759905176L;

  private Logger logger = Logger.getLogger(this.getClass().getName());

  private URL commonURL = null;

  private URL japeURL = null;

  private String inputASName, outputASName;

  private String locale = "en_GB";

  private String encoding = "UTF-8";

  private URL unitsURL = null;

  private MeasurementsParser parser = null;

  private Boolean failOnMissingInputAnnotations = Boolean.TRUE;
  
  private Boolean consumeNumberAnnotations = Boolean.TRUE;

  private Set<String> ignore = null;
  
  private MeasurementsTagger existingTagger = null;
  
  public MeasurementsTagger getExistingTagger() {
    if (existingTagger ==null) return this;    
    return existingTagger;
  }

  @Sharable
  public void setExistingTagger(MeasurementsTagger existingTagger) {
    this.existingTagger = existingTagger;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "Throw an exception when there are none of the required input annotations (Token) are present in the input set", defaultValue = "true")
  public void setFailOnMissingInputAnnotations(Boolean fail) {
    failOnMissingInputAnnotations = fail;
  }

  public Boolean getFailOnMissingInputAnnotations() {
    return failOnMissingInputAnnotations;
  }
  
  @RunTime
  @Optional
  @CreoleParameter(comment = "If true then Number annotations representing a measurement value will be consumed", defaultValue = "true")
  public void setConsumeNumberAnnotations(Boolean consume) {
    consumeNumberAnnotations = consume;
  }

  public Boolean getConsumeNumberAnnotations() {
    return consumeNumberAnnotations;
  }

  public String getLocale() {
    return locale;
  }

  @CreoleParameter(comment = "The locale to use for normalizing measurement values", defaultValue = "en_GB")
  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getEncoding() {
    return encoding;
  }

  @CreoleParameter(comment = "The encoding used for all the configuration files", defaultValue = "UTF-8")
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  public String getInputASName() {
    return inputASName;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "The name for annotation set used as input to the tagger")
  public void setInputASName(String inputASName) {
    this.inputASName = inputASName;
  }

  public String getOutputASName() {
    return outputASName;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "The name for annotation set used for the generated annotations")
  public void setOutputASName(String outputASName) {
    this.outputASName = outputASName;
  }

  private LanguageAnalyser jape = null;

  public URL getCommonURL() {
    return commonURL;
  }

  @Optional
  @CreoleParameter(comment = "A file of common words that should not be treated as units unless they are part of a compound unit", defaultValue = "resources/common_words.txt")
  public void setCommonURL(URL gazURL) {
    this.commonURL = gazURL;
  }

  public URL getJapeURL() {
    return japeURL;
  }

  @CreoleParameter(comment = "The JAPE file that drives the tagging process", defaultValue = "resources/jape/main.jape")
  public void setJapeURL(URL japeURL) {
    this.japeURL = japeURL;
  }

  public URL getUnitsURL() {
    return unitsURL;
  }

  @CreoleParameter(comment = "The units database file", defaultValue = "resources/units.dat")
  public void setUnitsURL(URL unitsURL) {
    this.unitsURL = unitsURL;
  }

  public Set<String> getIgnoredAnnotations() {
    return ignore;
  }

  @Optional
  @RunTime
  @CreoleParameter(comment = "Annotations in which a measurement cannot occur", defaultValue = "Date;Money")
  public void setIgnoredAnnotations(Set<String> ignore) {
    this.ignore = ignore;
  }

  /**
   * Get access to the underlying parser used for recognising measurements.
   * 
   * @return the instance of MeasurementsParser used by this PR instance.
   */
  public MeasurementsParser getParser() {
    return parser;
  }

  @Override
  public Resource init() throws ResourceInstantiationException {

    // do some sanity checking of the init time params so we can throw sensible
    // exceptions rather than random null pointers which don't help anybody

    if(locale == null || locale.trim().length() == 0)
      throw new ResourceInstantiationException(
              "locale must be specified before measurement tagger can be initialized!");

    if(encoding == null || encoding.trim().length() == 0)
      throw new ResourceInstantiationException(
              "encoding must be specified before measurement tagger can be initialized!");

    if(unitsURL == null)
      throw new ResourceInstantiationException(
              "the URL to the units data file must be specified before the measurement tagger can be initialized!");

    if(japeURL == null)
      throw new ResourceInstantiationException(
              "the URL to the JAPE grammer must be specified before the measurement tagger can be initialized!");

    try {
      // create the underlying parser
      parser = new MeasurementsParser(encoding, locale, unitsURL, commonURL);
    } catch(IOException e) {
      // if any problems occur at this point then we need to stop as there is
      // nothing we can do, we certainly can't parse anything!
      throw new ResourceInstantiationException(e);
    }

    if (existingTagger != null) {
      if (jape != null) {
        Factory.deleteResource(jape);
      }
      
      jape = (LanguageAnalyser)Factory.duplicate(existingTagger.jape);
      
      //TODO might need to duplicate the parsers as well if we can
    }
    else {
      // create the init params for the embedded JAPE grammar
      FeatureMap params = Factory.newFeatureMap();
      params.put("grammarURL", japeURL);
      params.put("encoding", encoding);
  
      if(jape == null) {
        // if this is the first time we are running init then actually create a
        // new transducer as we don't already have one
        FeatureMap hidden = Factory.newFeatureMap();
        Gate.setHiddenAttribute(hidden, true);
        jape =
                (LanguageAnalyser)Factory.createResource("gate.jape.plus.Transducer",
                        params, hidden);
      } else {
        // we are being run through a call to reInit so simply re-init the
        // underlying JAPE transducer
        jape.setParameterValues(params);
        jape.reInit();
      }
    }

    // we have been successful so return ourselves to whoever wanted us
    return this;
  }

  @Override
  public void cleanup() {
    // we are being closed so delete the embedded JAPE transducer as well in
    // order to make sure we don't leak any resources
    Factory.deleteResource(jape);
  }

  @Override
  public void execute() throws ExecutionException {

    // we need repeated access to the input annotation set so get a local handle
    // to it, in order to make the following easier to read and to reduce the
    // number of method calls we need to make
    AnnotationSet inputAS = document.getAnnotations(inputASName);

    // not every document will contain numbers/measurements but everything
    // should have been at least tokenized so let's check for that
    AnnotationSet tokens = inputAS.get(TOKEN_ANNOTATION_TYPE);
    if(tokens == null || tokens.size() < 1) {
      if(failOnMissingInputAnnotations) {
        // no tokens and the PR is set to fail on missing input annotations so
        // just throw the appropriate exception
        throw new ExecutionException("No tokens to process in document "
                + document.getName() + "\n" + "Please run a tokeniser first!");
      }

      // there are no tokens but we have been asked not to fail so log this
      // warning and then return cleanly so the rest of the pipeline can
      // continue to run to completion
      Utils.logOnce(
              logger,
              Level.INFO,
              "Measurement Tagger: no token annotations in input document - see debug log for details.");
      logger.debug("No input annotations in document " + document.getName());
      return;
    }

    // right let's start finding measurements!
    long startTime = System.currentTimeMillis();
    fireStatusChanged("Tagging Measurements in " + document.getName());

    if(ignore != null && ignore.size() > 0) {
      // if there are annotations we should ignore then...

      // create an empty feature map as we don't support selecting annotations
      // by feature name so we never need to put anything in here
      FeatureMap empty = Factory.newFeatureMap();

      for(String aName : ignore) {
        // for each annotation type we want to ignore...

        // get the annotations from the input set
        AnnotationSet toIgnore = inputAS.get(aName);

        for(Annotation a : toIgnore) {
          try {
            // for each annotation we want to ignore add a new
            // "CannotBeAMeasurement" annotation which can then be used by the
            // embedded JAPE to skip over sections of a document
            inputAS.add(a.getStartNode().getOffset(), a.getEndNode()
                    .getOffset(), "CannotBeAMeasurement", empty);
          } catch(Exception e) {
            // this should be impossible as the only offsets we are using are
            // coming straight from the document
          }
        }
      }
    }

    // configure the JAPE transducer with the correct document, input/output
    // sets and a reference to the parser that Java based RHS actions can access
    // through the ActionContext
    jape.getFeatures().put("measurementsParser", parser);
    jape.getFeatures().put("consumeNumberAnnotations", consumeNumberAnnotations);
    
    try {
      jape.setParameterValue("inputASName", inputASName);
      jape.setParameterValue("outputASName", outputASName);
    }
    catch (ResourceInstantiationException rie) {
      throw new ExecutionException(rie);
    }
    
    //jape.setInputASName(inputASName);
    //jape.setOutputASName(outputASName);
    jape.setDocument(getDocument());

    // use the progress of the JAPE as the progress of this whole PR
    //jape.addProgressListener(this);

    // run the JAPE and then clean up properly
    try {
      jape.execute();
    } finally {
      jape.setDocument(null);
    }

    // let everyone who is interested know that we have now finished
    fireStatusChanged(document.getName()
            + " tagged with Measurements in "
            + NumberFormat.getInstance().format(
                    (double)(System.currentTimeMillis() - startTime) / 1000)
            + " seconds!");
  }

  @Override
  public synchronized void interrupt() {
    super.interrupt();
    jape.interrupt();
  }

  @Override
  public void progressChanged(int i) {
    fireProgressChanged(i);
  }

  @Override
  public void processFinished() {
    fireProcessFinished();
  }
}
