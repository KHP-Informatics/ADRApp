/*
 * DateAnnotationNormalizer.java
 * 
 * Copyright (c) 2013, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 * 
 * Mark A. Greenwood, 13/03/2013
 */
package gate.creole.dates;

import gate.Annotation;
import gate.AnnotationSet;
import gate.FeatureMap;
import gate.Utils;
import gate.creole.ExecutionException;
import gate.creole.ExecutionInterruptedException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.RunTime;

import java.text.DateFormat;
import java.util.Date;
import java.util.regex.Pattern;

import mark.util.DateParser;
import mark.util.ParsePositionEx;

@CreoleResource(name = "Date Annotation Normalizer", icon = "date-normalizer.png", comment = "provides normalized values for all existing date annotations", helpURL = "http://gate.ac.uk/userguide/sec:misc-creole:datenormalizer")
public class DateAnnotationNormalizer extends DateNormalizer {

  private String annotationFeature;
  
  private Boolean wholeMatchOnly;
    
  @RunTime
  @CreoleParameter(defaultValue = "string", comment = "the annotation feature to normalize")
  public void setAnnotationFeature(String name) {
    annotationFeature = name;
  }

  public String getAnnotationFeature() {
    return annotationFeature;
  }
  
  @RunTime
  @CreoleParameter(defaultValue = "true", comment = "only normalize if the whole feature is parsed")
  public void setWholeMatchOnly(Boolean wholeMatchOnly) {
    this.wholeMatchOnly = wholeMatchOnly;
  }
  
  public Boolean getWholeMatchOnly() {
    return wholeMatchOnly;
  }
      
  @Override
  protected void annotate(Date documentDate, DateParser dp, DateFormat df) throws ExecutionException {    
    AnnotationSet dates = document.getAnnotations(getInputASName()).get(getAnnotationName());
        
    ParsePositionEx pp = new ParsePositionEx();
    
    float progress = 0;
    for (Annotation date : dates) {

      // if we have been asked to stop then do so
      if(isInterrupted()) { throw new ExecutionInterruptedException(
          "The execution of the \"" + getName()
              + "\" Date Normalizer has been abruptly interrupted!"); }
            
      FeatureMap params = date.getFeatures();
      
      String text = (String)params.get(annotationFeature);
      
      if (text == null && annotationFeature.equals("string")) text = Utils.stringFor(document, date);
      
      try {
        // try and parse the document content starting from the beginning of the
        // current token
        Date d =
            dp.parse(text, pp.reset(0), documentDate);
        
        if(d == null) {
          //if the text didn't parse skip on to the next character and try again
          //start++;
          continue;
        }
        
        if (wholeMatchOnly && pp.getIndex() != text.length()) {
          continue;
        }
        
        // normalize the date and store the value
        params.put("normalized", getNumericOutput()
            ? Integer.parseInt(df.format(d))
            : df.format(d));
        // set the complete feature based on the inferred flags from the parser
        if(pp.getFeatures().get("inferred").equals(DateParser.NONE)) {
          params.put("complete", "true");
        } else {
          params.put("complete", "false");
        }
        
        //store the inferred flags from the parser so people can have fine
        //grained control if they need it
        params.put("inferred", pp.getFeatures().get("inferred"));
        
        // copy the relative date feature from the parser into the feature map
        params.put("relative", pp.getFeatures().get("relative"));
        
        
      }
      finally {
        fireProgressChanged((int)((++progress / dates.size()) * 100));
      }
    }
  }
}
