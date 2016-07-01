/*
 * DateNormalizer.java
 * 
 * Copyright (c) 2010-2013, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 * 
 * Mark A. Greenwood, 16/11/2010
 */
package gate.creole.dates;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Factory;
import gate.FeatureMap;
import gate.Resource;
import gate.Utils;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ExecutionInterruptedException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mark.util.DateParser;
import mark.util.ParsePositionEx;

/**
 * A GATE PR which attempts to normalise dates within a document against the
 * date at which the document was written or published. This PR wraps the open
 * source <a
 * href="http://greenwoodma.servehttp.com/hudson/job/Date%20Parser/">Date
 * Parser</a> library to perform the normalisation.
 * 
 * @see <a href="http://gate.ac.uk/userguide/sec:misc-creole:datenormalizer">The
 *      GATE User Guide</a>
 * @author Mark A. Greenwood
 */
@CreoleResource(name = "Date Normalizer", interfaceName = "gate.ProcessingResource", icon = "date-normalizer.png", comment = "provides normalized values for all known dates", helpURL = "http://gate.ac.uk/userguide/sec:misc-creole:datenormalizer")
public class DateNormalizer extends AbstractLanguageAnalyser {
  private static final long serialVersionUID = -6580533128028166284L;

  /**
   * A comparator that orders annotations by priority (an Integer annotation
   * feature) and then by offset
   */
  private static final Comparator<Annotation> PRIORITY_ORDER =
      new Comparator<Annotation>() {
        public int compare(Annotation a1, Annotation a2) {
          Integer p1 = 0;
          try {
            if(a1.getFeatures().containsKey("priority"))
              p1 = Integer.valueOf(a1.getFeatures().get("priority").toString());
          } catch(Exception e) {
            // ignore this and use a priority of 0
          }
          Integer p2 = 0;
          try {
            if(a2.getFeatures().containsKey("priority"))
              p2 = Integer.valueOf(a2.getFeatures().get("priority").toString());
          } catch(Exception e) {
            // ignore this and use a priority of 0
          }
          // order by highest priority first
          if(!p1.equals(p2)) return p2.compareTo(p1);
          // order by lowest offset first
          return a1.getStartNode().getOffset()
              .compareTo(a2.getStartNode().getOffset());
        }
      };

  private String inputASName = null;

  @RunTime
  @Optional
  @CreoleParameter(comment = "the annotation set containing document date annotations to use for normalization")
  public void setInputASName(String name) {
    inputASName = name;
  }

  public String getInputASName() {
    return inputASName;
  }

  private String outputASName = null;

  @RunTime
  @Optional
  @CreoleParameter(comment = "the annotation set used to store output from this PR")
  public void setOutputASName(String name) {
    outputASName = name;
  }

  public String getOutputASName() {
    return outputASName;
  }

  private String annotationName = null;

  @RunTime
  @CreoleParameter(defaultValue = "Date", comment = "the annotation type produced by the PR")
  public void setAnnotationName(String name) {
    annotationName = name;
  }

  public String getAnnotationName() {
    return annotationName;
  }

  private List<String> documentDates = null;

  @RunTime
  @Optional
  @CreoleParameter(comment = "the name of the annotations or document feature which hold the date of the document")
  public void setSourceOfDocumentDate(List<String> names) {
    documentDates = names;
  }

  public List<String> getSourceOfDocumentDate() {
    return documentDates;
  }

  private String normalizedDocumentFeature;

  @RunTime
  @Optional
  @CreoleParameter(comment = "the name of the document feature in which to put the normalized document date", defaultValue = "normalized-date")
  public void setNormalizedDocumentFeature(String normalizedDocumentFeature) {
    this.normalizedDocumentFeature = normalizedDocumentFeature;
  }

  public String getNormalizedDocumentFeature() {
    return normalizedDocumentFeature;
  }

  private String dateFormat = null;

  @RunTime
  @Optional
  @CreoleParameter(defaultValue = "dd/MM/yyyy", comment = "the format to store normalized dates in")
  public void setDateFormat(String format) {
    dateFormat = format;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  private String localeName = null;

  private Locale locale = null;

  @Optional
  @CreoleParameter(comment = "the locale to use for parsing dates, in the form lang(_country(_variant)?)? -- if left blank the system locale will be used")
  public void setLocale(String locale) {
    localeName = locale;
  }

  public String getLocale() {
    return localeName;
  }

  private Boolean numericOutput = Boolean.FALSE;

  @RunTime
  @CreoleParameter(comment = "if true the date is converted into a number of the form yyyymmdd and storred as an Integer", defaultValue = "false")
  public void setNumericOutput(Boolean numericOutput) {
    this.numericOutput = numericOutput;
  }

  public Boolean getNumericOutput() {
    return numericOutput;
  }

  private boolean failOnMissingInputAnnotations = true;

  @RunTime
  @CreoleParameter(comment = "Throw an exception when there are none of the required input annotations", defaultValue = "true")
  public void setFailOnMissingInputAnnotations(Boolean fail) {
    failOnMissingInputAnnotations = fail;
  }

  public Boolean getFailOnMissingInputAnnotations() {
    return failOnMissingInputAnnotations;
  }
  
  @Override
  public Resource init() throws ResourceInstantiationException {
    // if a locale was specified in the init params then try and create such a
    // locale now so we don't have to do it for each document
    if(localeName != null && !localeName.trim().equals("")) {
      locale = DateParser.getLocale(localeName.trim());
      if(locale == null)
        throw new ResourceInstantiationException("The locale specified, '"
            + localeName + "' is not valid");
    }   
    
    return this;
  }

  @Override
  public void execute() throws ExecutionException {
    // assume we haven't been interrupted yet
    interrupted = false;
    // fire some progress notifications
    long startTime = System.currentTimeMillis();
    fireStatusChanged("Performing date normalization in " + document.getName());
    fireProgressChanged(0);
    // if there is no document to process then stop right now
    if(document == null)
      throw new ExecutionException("No document to process!");
    // we are supposed to be giving text output but there is no format specified
    // to stop right now
    if(!numericOutput
        && (dateFormat == null || dateFormat.trim().length() == 0))
      throw new ExecutionException("no date format specified");
    if(annotationName == null)
      throw new ExecutionException("Output annotation type must be specified");
    // configure the formatter using the format specified by the user
    DateFormat df =
        new SimpleDateFormat(numericOutput ? "yyyyMMdd" : dateFormat);
    // determine the locale to use for parsing. Check the document first
    // then the init time param and then finally use the default
    Locale docLocale = null;
    docLocale =
        DateParser.getLocale((String)document.getFeatures().get("locale"));
    if(docLocale == null) docLocale = locale;
    if(docLocale == null) docLocale = Locale.getDefault();

    // create an instance of the parser
    //TODO cache the parser for the init time locale
    DateParser dp = new DateParser(docLocale);
    
    // get handles to the document content and the input and output annotation
    // sets so that we can easily refer to them later
    AnnotationSet inputAS = document.getAnnotations(inputASName);
    
    // a parse position that will help us to parse the document dates
    ParsePositionEx pp = new ParsePositionEx();
    // lets try and figure out what date the document was written on
    Date documentDate = null;
    if(documentDates != null) {
      // a holder for the document date string
      String dd = null;
      for(String dateFeature : documentDates) {
        // for each document source the user has specified...
        try {
          // split the source at a . and assume it's of the form
          // Annotation.Feature
          String[] parts = dateFeature.split("\\.", 2);
          // try and get the annotations from the document
          List<Annotation> annotations =
              new ArrayList<Annotation>(inputAS.get(parts[0],
                  Factory.newFeatureMap()));
          if(annotations.size() > 0) {
            // if there are annotations then sort them by their priority
            Collections.sort(annotations, PRIORITY_ORDER);
            for(Annotation a : annotations) {
              // for each of the annotations get the string that might be a date
              // either from the specified feature or from the underlying string
              if(parts.length == 1) {
                dd =
                    Utils.stringFor(document, a);
              } else {
                dd = (String)a.getFeatures().get(parts[1]);
              }
              // try and parse the string we have just extracted
              Date d = dp.parse(dd, pp.reset(), null);
              if(d != null
                  && pp.getFeatures().get("inferred").equals(DateParser.NONE)) {
                // we have found a fully specified date so let's assume this is
                // the date of the document and stop
                documentDate = d;
                break;
              }
            }
          } else if(document.getFeatures().containsKey(dateFeature)) {
            // the document date source is actually a document feature so let's
            // parse that instead
            /*dd = (String)document.getFeatures().get(dateFeature);
            Date d = dp.parse(dd, pp.reset(), null);*/
            Date d = parseDocDate(dp, pp.reset(), document.getFeatures().get(dateFeature));
            /*if(d != null
                && pp.getFeatures().get("inferred").equals(DateParser.NONE)) {
              // we have found a fully specified date so let's assume this is
              // the date of the document and stop
              documentDate = d;
              break;
            }*/
            if (d != null) {
              documentDate = d;
              break;
            }
           
          }
        } catch(Exception e) {
          // ignore this and try the next feature
        }
        if(documentDate != null) break;
      }
    }
    // if there is no document date then assume the document was written today
    if(documentDate == null) documentDate = new Date();
    // if a normalized date feature has been specified then store the document
    // date we have just found in that feature
    if(normalizedDocumentFeature != null
        && normalizedDocumentFeature.trim().length() > 0) {
      document.getFeatures().put(
          normalizedDocumentFeature,
          (numericOutput ? Integer.parseInt(df.format(documentDate)) : df
              .format(documentDate)));
    }
    
    annotate(documentDate, dp, df);
    
    // we have finished so update anyone who cares
    fireProcessFinished();
    fireStatusChanged("Dates detected and normalized in \""
        + document.getName()
        + "\" in "
        + NumberFormat.getInstance().format(
            (double)(System.currentTimeMillis() - startTime) / 1000)
        + " seconds!");
  }
  
  @SuppressWarnings("deprecation")
  private Date parseDocDate(DateParser dp, ParsePositionEx pp, Object obj) {
    if (obj instanceof Integer) {
      //this handles dates which are integers which match the patter yyyymmdd
      String date = obj.toString();
      if (date.length() == 8) {
        return new Date(Integer.parseInt(date.substring(0,4))-1900, Integer.parseInt(date.substring(4,6))-1, Integer.parseInt(date.substring(6))); 
      }
    }
    
    if (obj instanceof Long) {
      //if the value is a long then assume it's a Unix time value
      return new Date((Long)obj);
    }
    
    //we have some other value so treat it as a string to parse
    Date d = dp.parse(obj.toString(), pp.reset(), null);
    if(d != null
        && pp.getFeatures().get("inferred").equals(DateParser.NONE)) {
      // we have found a fully specified date so let's assume this is
      // the date of the document and stop
      return d;
    }
    
    return null;
  }
  
  /**
   * This method actually does all the work, but it is here so it can be
   * overriden by subclasses to do things differently
   */
  protected void annotate(Date documentDate, DateParser dp, DateFormat df) throws ExecutionException {
    
    //now we have a parser create a regexp to look for possible dates
    StringBuilder pattern = new StringBuilder("\\b((([0-9]|[0-9]{2}|[0-9]{4})[^0-9])");    
    for (String word : dp.getWords()) {
      if (word.length() > 0) pattern.append("|(").append(word).append(")");
    }    
    pattern.append(")");
    Pattern finder = Pattern.compile(pattern.toString(),Pattern.CASE_INSENSITIVE);
    
    String docContent = document.getContent().toString();
    
    //get a matcher for possible dates over the content
    Matcher m = finder.matcher(docContent);
    
    ParsePositionEx pp = new ParsePositionEx();
    
    AnnotationSet outputAS = document.getAnnotations(outputASName);
    
    int start = 0;
    while (m.find()) {
            
      if (m.start(1) <= start) continue;
      
      // for each match of the regexp....
      
      // if we have been asked to stop then do so
      if(isInterrupted()) { throw new ExecutionInterruptedException(
          "The execution of the \"" + getName()
              + "\" Date Normalizer has been abruptly interrupted!"); }

      try {
        // try and parse the document content starting from the beginning of the
        // current token
        Date d =
            dp.parse(docContent, pp.reset(m.start(1)), documentDate);
        
        if(d == null) {
       // if the text didn't parse skip on to the next character and try again
          start++;
          continue;
        }
        // create a FeatureMap to hold the parameters of the annotation we are
        // about to create
        FeatureMap params = Factory.newFeatureMap();
        // normalize the date and store the value
        params.put("normalized", numericOutput
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
        
        //store the accurate flags from the parser so people can have fine
        //grained control if they need it
        params.put("accurate", pp.getFeatures().get("accurate"));
        
        // copy the relative date feature from the parser into the feature map
        params.put("relative", pp.getFeatures().get("relative"));
        // now create the annotation
        outputAS.add((long)m.start(1), (long)pp.getIndex(), annotationName, params);
        // move parsing on to the end of the annotation we have just created in
        // order to avoid overlapping annotations
        start = pp.getIndex();
      } catch(Exception e) {
        e.printStackTrace();
        // not quite sure how we got here but continue on by moving parsing to
        // the next character in the document
        start++;
      }
      // Calculate percentage complete using the parsing position within the
      // document content
      fireProgressChanged((int)(((float)start / docContent.length()) * 100));
    }
  }
}
