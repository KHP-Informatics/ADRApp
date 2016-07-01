/*
 * Copyright (c) 2009-2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */
package gate.creole.numbers;

import static gate.GateConstants.ORIGINAL_MARKUPS_ANNOT_SET_NAME;
import static gate.creole.numbers.AnnotationConstants.HINT_FEATURE_NAME;
import static gate.creole.numbers.AnnotationConstants.NUMBER_ANNOTATION_NAME;
import static gate.creole.numbers.AnnotationConstants.TYPE_FEATURE_NAME;
import static gate.creole.numbers.AnnotationConstants.VALUE_FEATURE_NAME;
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
import gate.creole.ExecutionInterruptedException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.creole.metadata.Sharable;
import gate.gui.ActionsPublisher;
import gate.util.BomStrippingInputStreamReader;
import gate.util.InvalidOffsetException;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.StaxDriver;

/**
 * A GATE PR which annotates numbers which appear as both words or
 * numbers (or a combination) and determines their numeric value. Whilst
 * useful on their own the annotations produced can also be used as a
 * preliminary step towards more complex annotations such as
 * measurements or monetary units.
 * 
 * @see <a
 *      href="http://gate.ac.uk/userguide/sec:misc-creole:numbers:numbers">The
 *      GATE User Guide</a>
 * @author Mark A. Greenwood
 * @author Thomas Heitz
 */
@CreoleResource(name = "Numbers Tagger", comment = "Finds numbers in (both words and digits) and annotates them with their numeric value", icon = "numbers.png", helpURL = "http://gate.ac.uk/userguide/sec:misc-creole:numbers:numbers")
public class NumbersTagger extends AbstractLanguageAnalyser implements ActionsPublisher {

  private static final long serialVersionUID = 8568794158677464398L;

  private transient Logger logger = Logger.getLogger(this.getClass().getName());

  private transient Config config;

  private URL configURL;

  private URL postProcessURL;

  private LanguageAnalyser jape;

  private String encoding;

  private String annotationSetName;

  private Boolean failOnMissingInputAnnotations = Boolean.TRUE;

  private Boolean allowWithinWords = Boolean.FALSE;

  private Boolean useHintsFromOriginalMarkups = Boolean.TRUE;

  private Pattern pattern;

  private Pattern subPattern;

  private Pattern numericPattern;
  
  private NumbersTagger existingTagger;
  
  public NumbersTagger getExistingTagger() {
    if (existingTagger == null) return this;
    return existingTagger;
  }

  @Sharable
  public void setExistingTagger(NumbersTagger existingTagger) {
    this.existingTagger = existingTagger;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "Throw an exception when there are none of the required input annotations (Token and Sentence)", defaultValue = "true")
  public void setFailOnMissingInputAnnotations(Boolean fail) {
    failOnMissingInputAnnotations = fail;
  }

  public Boolean getFailOnMissingInputAnnotations() {
    return failOnMissingInputAnnotations;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "Use annotations from the Original markups as hints for annotating numbers", defaultValue = "true")
  public void setUseHintsFromOriginalMarkups(Boolean useHints) {
    useHintsFromOriginalMarkups = useHints;
  }

  public Boolean getUseHintsFromOriginalMarkups() {
    return useHintsFromOriginalMarkups;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "Allow numbers to appear within words", defaultValue = "false")
  public void setAllowWithinWords(Boolean allowWithinWords) {
    this.allowWithinWords = allowWithinWords;
  }

  public Boolean getAllowWithinWords() {
    return allowWithinWords;
  }

  public String getAnnotationSetName() {
    return annotationSetName;
  }
  
  public NumbersTagger() {
    boolean DEBUG_DUPLICATION = true;
    if(DEBUG_DUPLICATION) {
      actions.add(new AbstractAction("Duplicate") {
  
        @Override
        public void actionPerformed(ActionEvent arg0) {
          try {
            Factory.duplicate(NumbersTagger.this);
          } catch(ResourceInstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }          
        
      });
    }
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "The name of annotation set used for the generated annotations")
  public void setAnnotationSetName(String outputAnnotationSetName) {
    this.annotationSetName = outputAnnotationSetName;
  }

  public URL getConfigURL() {
    return configURL;
  }

  @CreoleParameter(defaultValue = "resources/languages/all.xml", suffixes = ".xml")
  public void setConfigURL(URL url) {
    configURL = url;
  }

  public URL getPostProcessURL() {
    return postProcessURL;
  }

  @CreoleParameter(defaultValue = "resources/jape/post-process.jape", suffixes = ".jape")
  public void setPostProcessURL(URL url) {
    postProcessURL = url;
  }

  @CreoleParameter(defaultValue = "UTF-8")
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  public String getEncoding() {
    return encoding;
  }

  /**
   * Turns an array of words into a FeatureMap containing their total
   * numeric value and the type of words used
   * 
   * @param words an sequence of words that represent a number
   * @return a FeatureMap detailing the numeric value and type of the
   *         number, or null if the words are not a number
   */
  private FeatureMap calculateValue(String... words) {

    boolean hasWords = false;
    boolean hasNumbers = false;

    // contains the values for hundred, thousand, million, etc.
    TreeMap<Double, Double> values = new TreeMap<Double, Double>();

    // initialise to zero all the values
    // key 0 is for 0-99
    // key 2 is for 100-999
    // key 3 is for 1000-999 999
    // etc.
    values.put(0d, 0d);
    for(Multiplier m : config.multipliers.values()) {
      if(m.type.equals(Multiplier.Type.BASE_10)) values.put(m.value, 0d);
    }

    // for each word

    for(String word : words) {
      Double value;
      Multiplier multiplier;
      if(word.matches(numericPattern.pattern())) {
        // the word is actually a number in numbers so convert the
        // decimal and
        // grouping symbols to the normal Java versions and then parse
        // as a if
        // it was a normal double representation
        values.put(0d, values.get(0d)
                + Double.parseDouble(word.replaceAll(
                        Pattern.quote(config.digitGroupingSymbol), "")
                        .replaceAll(Pattern.quote(config.decimalSymbol), ".")));
        hasNumbers = true;
      }
      else if((value = config.words.get(word.toLowerCase())) != null) {
        // the word is a normal number so store it
        values.put(0d, values.get(0d) + value);
        hasWords = true;
      }
      else if((multiplier = config.multipliers.get(word.toLowerCase())) != null) {
        // the word is a multiplier so...
        value = multiplier.value;
        if(multiplier.type.equals(Multiplier.Type.FRACTION)) {
          double sum = 0;
          for(double power : values.keySet()) {
            //System.out.println(power+", "+values.get(power)+", " +(values.get(power) / value));
            values.put(power, values.get(power) / value);
            sum += values.get(power);
          }

          if(sum == 0) {
            values.put(0d, 1/value);
          }
        }
        else if(multiplier.type.equals(Multiplier.Type.POWER)) {
          double sum = 0;
          for(double power : values.keySet()) {
            // raise values in each to actual power and then square etc.
            // and then root from the power
            double actual = values.get(power) * Math.round(Math.pow(10, power));

            actual = Math.pow(actual,value);
            
            actual = actual / Math.round(Math.pow(10, power));
            
            values.put(power, actual);
            sum += values.get(power);
          }

          if(sum == 0) {
            values.put(0d, 1d/value);
          }
        }
        else {

          int sum = 0;
          for(double power : values.keySet()) {
            if(power == value) {
              break;
            }
            // move all values from inferior powers to the current power
            values.put(value, values.get(value) + values.get(power)
                    * Math.round(Math.pow(10, power)));
            sum += values.get(power);

            // reset value for this inferior power
            values.put(power, 0D);
          }
          if(sum == 0) {
            // 'a thousand' -> 1000
            values.put(value, 1D);
          }
        }
        hasWords = true;
      }
      else {
        // this isn't anything we know about so the whole sequence can't
        // be
        // valid and so we need to return null
        return null;
      }
    }

    double result = 0;
    // sum up all values and multiply them by their multipliers
    for(double v : values.keySet()) {
      result += values.get(v) * Math.pow(10, v);
    }

    // determine the type of number
    String type = "numbers";
    if(hasWords && hasNumbers)
      type = "wordsAndNumbers";
    else if(hasWords) type = "words";

    // create the FeatureMap describing the sequence of words
    FeatureMap fm = Factory.newFeatureMap();
    fm.put(VALUE_FEATURE_NAME, result);
    fm.put(TYPE_FEATURE_NAME, type);

    return fm;
  }

  @Override
  public void execute() throws ExecutionException {

    // assume we haven't been asked to stop just yet
    interrupted = false;

    // if there is no document then argh!
    if(document == null) throw new ExecutionException("No Document provided!");

    // get the annotation set we will be working with
    AnnotationSet annotationSet = document.getAnnotations(annotationSetName);

    // the post-processing requires Token annotations so lets check we
    // have some
    // now before we do any more work
    AnnotationSet tokens = annotationSet.get(TOKEN_ANNOTATION_TYPE);
    if(tokens == null || tokens.size() < 1) {
      if(failOnMissingInputAnnotations) {
        throw new ExecutionException("No tokens to process in document "
                + document.getName() + "\n" + "Please run a tokeniser first!");
      }

      Utils
              .logOnce(
                      logger,
                      Level.INFO,
                      "Numbers Tagger: no token annotations in input document - see debug log for details.");
      logger.debug("No input annotations in document " + document.getName());
      return;
    }

    // the post-processing requires Sentence annotations so lets check
    // we have
    // some
    // now before we do any more work
    AnnotationSet sentences = annotationSet.get(SENTENCE_ANNOTATION_TYPE);
    if(sentences == null || sentences.size() < 1) {
      if(failOnMissingInputAnnotations) {
        throw new ExecutionException("No sentences to process in document "
                + document.getName() + "\n"
                + "Please run a sentence splitter first!");
      }

      Utils
              .logOnce(
                      logger,
                      Level.INFO,
                      "Numbers Tagger: no sentence annotations in input document - see debug log for details.");
      logger.debug("No input annotations in document " + document.getName());
      return;
    }

    // fire some progress notifications
    long startTime = System.currentTimeMillis();
    fireStatusChanged("Tagging Numbers in " + document.getName());
    fireProgressChanged(0);

    // get the text of the document
    String text = document.getContent().toString();

    // now find all numeric numbers in the text
    Matcher matcher = numericPattern.matcher(text);
    while(matcher.find()) {
      // if we have been asked to stop then do so
      if(isInterrupted()) {
        throw new ExecutionInterruptedException("The execution of the \""
                + getName()
                + "\" Numbers Tagger has been abruptly interrupted!");
      }

      // get the value of the number
      FeatureMap fm = calculateValue(matcher.group());
      if(fm != null) {
        try {
          // create a new annotation with the right features
          annotationSet.add((long)matcher.start(), (long)matcher.end(),
                  NUMBER_ANNOTATION_NAME, fm);
        }
        catch(InvalidOffsetException e) {
          // this can never happen!
        }
      }
    }

    // now find all the long sequences of words and numbers
    matcher = pattern.matcher(text);

    while(matcher.find()) {
      // if we have been asked to stop then do so
      if(isInterrupted()) {
        throw new ExecutionInterruptedException("The execution of the \""
                + getName()
                + "\" Numbers Tagger has been abruptly interrupted!");
      }

      // split the sequence of numbers into a list
      // TODO can we do this from the matching groups of the main regex?
      List<String> words = new ArrayList<String>();
      Matcher subMatcher = subPattern.matcher(matcher.group());
      while(subMatcher.find()) {
        // get each number
        words.add(subMatcher.group(1));
      }

      // convert the sequence of words to a number
      FeatureMap fm = calculateValue(words.toArray(new String[words.size()]));

      if(fm != null) {
        try {
          // if there are already number annotations at this point then
          // it must
          // be from the numeric only bit above so remove them as they
          // are
          // superseded by the new annotation
          annotationSet.removeAll(annotationSet.getContained(
                  (long)matcher.start(), (long)matcher.end()).get(
                  NUMBER_ANNOTATION_NAME));

          // create the new annotation
          annotationSet.add((long)matcher.start(), (long)matcher.end(),
                  NUMBER_ANNOTATION_NAME, fm);
        }
        catch(InvalidOffsetException e) {
          // this can never happen
        }
      }
    }

    // again if we have been asked to stop then do so
    if(isInterrupted()) {
      throw new ExecutionInterruptedException("The execution of the \""
              + getName() + "\" Numbers Tagger has been abruptly interrupted!");
    }

    if(useHintsFromOriginalMarkups) {
      // look at the Oringal markups set for hints that might help the
      // jape
      AnnotationSet supAnnotations = document.getAnnotations(
              ORIGINAL_MARKUPS_ANNOT_SET_NAME).get("sup");
      for(Annotation sup : supAnnotations) {
        AnnotationSet numbers = annotationSet.getContained(sup.getStartNode()
                .getOffset(), sup.getEndNode().getOffset());

        for(Annotation num : numbers) {
          num.getFeatures().put(HINT_FEATURE_NAME, "sup");
        }
      }
    }

    try {
      // now configure the JAPE transducer ready for the post-processing
      // steps
      jape.setDocument(getDocument());
      jape.setParameterValue("inputASName", annotationSetName);
      jape.setParameterValue("outputASName", annotationSetName);

      // pass some of the configuration through so that JAPE rules can
      // make use
      // of it if they wish. These values can be accessed through the
      // ActionContext object available on the RHS of each JAPE rule.
      jape.getFeatures().put("decimalSymbol", config.decimalSymbol);
      jape.getFeatures().put("digitGroupingSymbol", config.digitGroupingSymbol);
      jape.getFeatures().put("allowWithinWords", allowWithinWords);

      // now run the JAPE transducer
      jape.execute();
    }
    catch(ResourceInstantiationException e) {
      // if for some reason we can't init the transducer properly then
      // turn this
      // into an execution problem and report it
      throw new ExecutionException(e);
    }
    finally {
      // make sure we release the document properly
      jape.setDocument(null);
    }

    // let anyone who cares know that we have now finished
    fireProcessFinished();
    fireStatusChanged(document.getName()
            + " tagged with Numbers in "
            + NumberFormat.getInstance().format(
                    (double)(System.currentTimeMillis() - startTime) / 1000)
            + " seconds!");
  }

  public void cleanup() {
    // when someone deletes us we need to delete the jape we created
    // otherwise
    // it becomes an orphan and eats memory that no one is willing to
    // pay for.
    // In a nightmare world it might even starting singing songs from
    // Oliver!
    Factory.deleteResource(jape);
  }

  public Resource init() throws ResourceInstantiationException {

    // do some initial sanity checking of the params
    if(configURL == null)
      throw new ResourceInstantiationException(
              "No configuration file specified!");

    if(postProcessURL == null)
      throw new ResourceInstantiationException(
              "No post-processing JAPE file specified!");

    try {
      // attempt to load the configuration from the supplied URL
      XStream xstream = Config.getXStream(configURL, getClass()
              .getClassLoader());
      BomStrippingInputStreamReader in = new BomStrippingInputStreamReader(
              configURL.openStream(), encoding);
      config = (Config)xstream.fromXML(in);
      in.close();
    }
    catch(Exception e) {
      throw new ResourceInstantiationException(e);
    }

    // sanity check the configuration of the decimal and grouping
    // symbols
    if(config.decimalSymbol.equals(config.digitGroupingSymbol))
      throw new ResourceInstantiationException(
              "The decimal symbol and digit grouping symbol must be different!");

    Set<String> allWords = config.words.keySet();
    Set<String> allMultipliers = config.multipliers.keySet();
    List<String> conjunctions = new ArrayList<String>(config.conjunctions
            .keySet());

    // sanity check the words and modifiers to ensure that they don't
    // overlap
    if(allWords.removeAll(allMultipliers))
      throw new ResourceInstantiationException(
              "The set of words and multipliers must be disjoint!");

    // combine the words and multipliers ready for creating a regex
    List<String> list = new ArrayList<String>();
    list.addAll(allWords);
    list.addAll(allMultipliers);

    // sanity check the conjunctions
    if(list.removeAll(conjunctions))
      throw new ResourceInstantiationException(
              "Conjunctions cannot also be words or multipliers!");

    // Create a comparator for sorting String elements by their length,
    // longest
    // first
    Comparator<String> lengthComparator = new Comparator<String>() {
      public int compare(String o1, String o2) {
        // sort by descending length
        return o2.length() - o1.length();
      }
    };

    // sort the conjunctions
    Collections.sort(conjunctions, lengthComparator);

    // build a regex from the conjunctions taking into account if they
    // need to
    // be surrounded by spaces or not
    StringBuilder withSpaces = new StringBuilder();
    StringBuilder withoutSpaces = new StringBuilder();
    for(String conjunction : conjunctions) {
      withSpaces.append("|").append(conjunction);
      if(!config.conjunctions.get(conjunction)) {
        withoutSpaces.append("|").append(conjunction);
      }
    }

    String separatorsRegex = "(?i:\\s{1,2}(?:-" + withSpaces
            + ")\\s{1,2}|\\s{1,2}|-" + withoutSpaces + ")";

    // create a regex for recognising numbers written using numbers
    // taking into
    // account the decimal and grouping symbols from the configuration
    // file
    String numericRegex = "[-+]?(?:(?:(?:(?:[0-9]+"
            + Pattern.quote(config.digitGroupingSymbol) + ")*[0-9]+(?:"
            + Pattern.quote(config.decimalSymbol) + "[0-9]+)?))|(?:"
            + Pattern.quote(config.decimalSymbol) + "[0-9]+))";

    numericPattern = Pattern.compile(numericRegex);

    // sort the words and multipliers
    Collections.sort(list, lengthComparator);

    // put all the words into one big or regex
    StringBuilder builder = new StringBuilder("(?i:");
    for(String word : list) {
      builder.append(Pattern.quote(word)).append("|");
    }
    String numbersRegex = builder.substring(0, builder.length() - 1);
    numbersRegex += ")";

    // build up a regexp allowing repeated words
    String regex = "(?:(?:" + numericRegex + separatorsRegex + "?)?(?:"
            + numbersRegex + separatorsRegex + "){0,10}" + numbersRegex + ")";

    // make sure the whole number doesn't appear within a word
    pattern = Pattern.compile("(?:^|(?<=\\s|[^\\p{Alnum}]))" + regex
            + "(?:(?=\\s|[^\\p{Alnum}])|$)", Pattern.CASE_INSENSITIVE
            + Pattern.UNICODE_CASE);

    // create a simple regexp for extracting each piece from a whole
    // number
    // TODO can we just use matching groups from the main regex
    regex = "(" + numbersRegex + "|" + numericRegex + ")" + separatorsRegex
            + "?";
    subPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE
            + Pattern.UNICODE_CASE);

    // now we need to create the JAPE transducer that is used for
    // post-processing of the numbers found by the complex regex
    FeatureMap params = Factory.newFeatureMap();
    params.put("grammarURL", postProcessURL);

    if (existingTagger != null) {
      if (jape != null) Factory.deleteResource(jape);      
      jape = (LanguageAnalyser)Factory.duplicate(existingTagger.jape);
    }
    else {
      if(jape == null) {
      
        // only create the transducer if it doesn't already exist
        FeatureMap hidden = Factory.newFeatureMap();
        Gate.setHiddenAttribute(hidden, true);
        jape = (LanguageAnalyser)Factory.createResource("gate.jape.plus.Transducer",
                params, hidden);
      }
      else {
        // if it exists just reinitialize it
        jape.setParameterValues(params);
        jape.reInit();
      }
    }

    return this;
  }

  /**
   * This class provides access to the configuration file in a simple
   * fashion. It is instantiated using XStream to map from the XML
   * configuration file to the Object structure.
   * 
   * @author Mark A. Greenwood
   */
  static class Config {

    private String description;

    private Map<String, Double> words;

    private Map<String, Multiplier> multipliers;

    private Map<String, Boolean> conjunctions;

    private Map<URL, String> imports;

    private String decimalSymbol;

    private String digitGroupingSymbol;

    public String toString() {
      return description + " -- words: " + words.size() + ", multiplies: "
              + multipliers.size() + ", conjunctions: " + conjunctions.size();
    }

    /**
     * Ensures that all fields have been initialised to useful values
     * after the instance has been created from the configuration file.
     * This includes creating default values if certain aspects have not
     * been specified and the importing of linked configuration files.
     * 
     * @return a correctly initialised Config object.
     */
    private Object readResolve() {

      // make sure every field has a sensible default value
      if(words == null) words = new HashMap<String, Double>();
      if(multipliers == null) multipliers = new HashMap<String, Multiplier>();
      if(conjunctions == null) conjunctions = new HashMap<String, Boolean>();

      if(decimalSymbol == null) decimalSymbol = ".";
      if(digitGroupingSymbol == null) digitGroupingSymbol = ",";

      if(imports == null) {
        imports = new HashMap<URL, String>();
      }
      else {
        for(Map.Entry<URL, String> entry : imports.entrySet()) {
          // for each import...
          URL url = entry.getKey();
          String encoding = entry.getValue();
          XStream xstream = getXStream(url, getClass().getClassLoader());

          BomStrippingInputStreamReader in = null;
          try {
            in = new BomStrippingInputStreamReader(url.openStream(), encoding);

            // load the config file and then...
            Config c = (Config)xstream.fromXML(in);

            // add all the words to this config object
            words.putAll(c.words);
            multipliers.putAll(c.multipliers);
            conjunctions.putAll(c.conjunctions);
          }
          catch(IOException ioe) {
            // ignore this for now
          }
          finally {
            if(in != null) {
              try {
                in.close();
              }
              catch(Exception e) {
                // damn stupid exception!
              }
            }
          }
        }
      }

      return this;
    }

    /**
     * Creates a correctly configured XStream for reading the XML
     * configuration files.
     * 
     * @param url the URL of the config file you are loading. This is
     *          required so that we can correctly handle relative paths
     *          in import statements.
     * @param cl the Classloader which has access to the classes
     *          required. This is needed as otherwise loading this
     *          through GATE we somehow can't find some of the classes.
     * @return an XStream instance that can load the XML config files
     *         for this PR.
     */
    static XStream getXStream(final URL url, ClassLoader cl) {

      if(url == null)
        throw new IllegalArgumentException(
                "You must specify the URL of the file you are processing");

      XStream xstream = new XStream(new StaxDriver());
      if(cl != null) xstream.setClassLoader(cl);

      xstream.alias("config", Config.class);

      // This is a custom HashMap converter that allows us to support
      // the rather
      // odd XML structure I created
      xstream.registerConverter(new Converter() {
        @SuppressWarnings("rawtypes")
        public boolean canConvert(Class type) {
          return type.equals(HashMap.class);
        }

        public void marshal(Object source, HierarchicalStreamWriter writer,
                MarshallingContext context) {
          throw new RuntimeException(
                  "Writing config files is not currently supported!");
          // if we do eventually support writing files then remember
          // that
          // OutputStreamWriter out = new OutputStreamWriter(new
          // FileOutputStream(f), "UTF-8");
        }

        @SuppressWarnings( {"rawtypes", "unchecked"})
        public Object unmarshal(HierarchicalStreamReader reader,
                UnmarshallingContext context) {
          HashMap map = new HashMap();
          while(reader.hasMoreChildren()) {

            try {
              if(reader.getNodeName().equals("imports")) {
                // Elements in this map look like
                // <url encoding="UTF-8">english.xml</url>

                String encoding = reader.getAttribute("encoding");
                reader.moveDown();
                String rURL = reader.getValue();
                reader.moveUp();
                map.put(new URL(url, rURL), encoding);
              }
              else if(reader.getNodeName().equals("conjunctions")) {
                // Elements in this map look like
                // <word whole="true">and</word>

                String value = reader.getAttribute("whole");
                reader.moveDown();
                String word = reader.getValue().toLowerCase();
                reader.moveUp();
                map.put(word, Boolean.parseBoolean(value));
              }
              else {
                // Elements in all other maps look like
                // <word value="3.0">three</word>

                // support numbers written as fractions to make like a
                // little
                // easier when configuring things like 1/3
                String[] values = reader.getAttribute("value").split("/");
                String type = reader.getAttribute("type");
                reader.moveDown();
                String word = reader.getValue().toLowerCase();
                reader.moveUp();

                double value = Double.parseDouble(values[0]);
                for(int i = 1; i < values.length; ++i) {
                  value = value / Double.parseDouble(values[i]);
                }

                if(reader.getNodeName().equals("multipliers")) {
                  map.put(word,
                          new Multiplier(value, type == null ? "e" : type));
                }
                else {
                  map.put(word, value);
                }
              }
            }
            catch(Exception e) {
              e.printStackTrace();
            }
          }
          return map;
        }
      });

      return xstream;
    }
  }

  private static class Multiplier {
    private enum Type {
      BASE_10("e"), FRACTION("/"), POWER("^");

      final String description;

      Type(String description) {
        this.description = description;
      }

      public static Type get(String type) {
        for(Type t : EnumSet.allOf(Type.class)) {
          if(t.description.equals(type)) return t;
        }

        throw new IllegalArgumentException("'" + type
                + "' is not a valid multiplier type type");
      }
    }

    Type type;

    Double value;

    public Multiplier(Double value, String type) {
      this.value = value;
      this.type = Type.get(type);
    }
  }
  
  private List<Action> actions = new ArrayList<Action>();

  @Override
  public List<Action> getActions() {
    return actions;
  }
}

