/*
 * Units is a program for unit conversion originally written in C by Adrian
 * Mariano (adrian@cam.cornell.edu.). Copyright (C) 1996, 1997, 1999, 2000,
 * 2001, 2002, 2003, 2004, 2005, 2006 by Free Software Foundation, Inc.
 * 
 * Java version Copyright (C) 2003, 2004, 2005, 2006, 2007 by Roman R
 * Redziejowski (roman.redz@tele2.se).
 * 
 * GATE version is Copyright (c) 2009-2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * Licensed under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */
package gate.creole.measurements;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A measurement parser based upon a modified version of the <a
 * href="http://units-in-java.sourceforge.net/">Java port</a> of the <a
 * href="http://www.gnu.org/software/units/">GNU Units</a> package.
 */
public class MeasurementsParser {

  private static final String DEFAULT_LOCALE = "en_GB";

  // The default file is encoded using UTF-8 although you need to use
  // ISO-8859-1 to read the original units.dat
  private static final String DEFAULT_ENCODING = "UTF-8";

  protected Set<String> commonWords = null;

  protected Map<String, Measurement> dimensions = null;

  /**
   * Construct an instance of the parser. Use sdefault values for encoding and
   * locale of UTF-8 and en_GB respectively. The main configuration file should
   * be in the same format as that accepted by the GNU Units package. The common
   * words file is a simple list of one unit per line. Units which appear in the
   * common word file will not be recognised if they appear on their own but
   * will be used in measurements with a compound unit. This file was manually
   * tuned over a large corpus of patent documents but may need to be edited for
   * specific domains.
   * 
   * @see #MeasurementsParser(String, String, URL, URL)
   * @see <a
   *      href="http://www.gnu.org/software/units/manual/html_node/Defining-new-units.html">Format
   *      of the GNU Units data file</a>
   * @param data
   *          the URL of the main data file
   * @param common
   *          the URL of the common words file
   * @throws IOException
   *           if an error occurs loading data from the specified configuration
   *           files
   */
  public MeasurementsParser(URL data, URL common) throws IOException {
    this(DEFAULT_ENCODING, DEFAULT_LOCALE, data, common);
  }

  /**
   * Construct an instance of the parser. The main configuration file should be
   * in the same format as that accepted by the GNU Units package. The common
   * words file is a simple list of one unit per line. Units which appear in the
   * common word file will not be recognised if they appear on their own but
   * will be used in measurements with a compound unit. This file was manually
   * tuned over a large corpus of patent documents but may need to be edited for
   * specific domains.
   * 
   * @see <a
   *      href="http://www.gnu.org/software/units/manual/html_node/Defining-new-units.html">Format
   *      of the GNU Units data file</a>
   * @param encoding
   *          the encoding of the two configuration files
   * @param locale
   *          the locale to use for parsing measurements
   * @param data
   *          the URL of the main data file
   * @param common
   *          the URL of the common words file
   * @throws IOException
   *           if an error occurs loading data from the specified configuration
   *           files
   */
  public MeasurementsParser(String encoding, String locale, URL data, URL common)
          throws IOException {

    // we must have valid encoding and data file URL parameters so check that we
    // have been provided with at least a value for each, we will find out if
    // they are sensible later
    if(encoding == null)
      throw new NullPointerException(
              "The encoding of the data file must be specified!");

    if(data == null)
      throw new NullPointerException(
              "The URL of the data file must be specified!");

    // create the maps used for internal storage of the parsed data files
    units = new HashMap<String, Unit>();
    prefixes = new HashMap<String, Prefix>();
    functions = new HashMap<String, DefinedFunction>();

    // read in the main data file
    readunits(data, encoding, locale);

    // make sure function names are also treated as units
    for(String func : functions.keySet()) {
      units.put(func, new Unit(func, "!", this));
    }

    // create a whole bunch of standard built-in functions
    builtInFunctions = new HashMap<String, BuiltInFunction>();
    builtInFunctions.put("sin", new BuiltInFunction("sin",
            BuiltInFunction.ANGLEIN, BuiltInFunction.SIN, this));
    builtInFunctions.put("cos", new BuiltInFunction("cos",
            BuiltInFunction.ANGLEIN, BuiltInFunction.COS, this));
    builtInFunctions.put("tan", new BuiltInFunction("tan",
            BuiltInFunction.ANGLEIN, BuiltInFunction.TAN, this));
    builtInFunctions.put("ln", new BuiltInFunction("ln",
            BuiltInFunction.DIMLESS, BuiltInFunction.LN, this));
    builtInFunctions.put("log", new BuiltInFunction("log",
            BuiltInFunction.DIMLESS, BuiltInFunction.LOG, this));
    builtInFunctions.put("log2", new BuiltInFunction("log2",
            BuiltInFunction.DIMLESS, BuiltInFunction.LOG2, this));
    builtInFunctions.put("exp", new BuiltInFunction("exp",
            BuiltInFunction.DIMLESS, BuiltInFunction.EXP, this));
    builtInFunctions.put("acos", new BuiltInFunction("acos",
            BuiltInFunction.ANGLEOUT, BuiltInFunction.ACOS, this));
    builtInFunctions.put("atan", new BuiltInFunction("atan",
            BuiltInFunction.ANGLEOUT, BuiltInFunction.ATAN, this));
    builtInFunctions.put("asin", new BuiltInFunction("asin",
            BuiltInFunction.ANGLEOUT, BuiltInFunction.ASIN, this));

    // Make sure radian is defined and if not create it.
    Unit r = units.get("radian");
    if(r != null)
      radian = r;
    else radian = new Unit("radian", "!", this);

    // load in any common words that have been provided
    commonWords = new HashSet<String>();
    if(common != null) {
      BufferedReader in = null;
      try {
        in = open(common, encoding);
        String word = in.readLine();
        while(word != null) {
          commonWords.add(word.trim());
          word = in.readLine();
        }
      } catch(IOException e) {
        throw new RuntimeException(e);
      } finally {
        if(in != null) {
          try {
            in.close();
          } catch(IOException ioe) {
            // ignore it
          }
        }
      }
    }

    // work through all the defined units to find those that define a dimension
    // such as length, area, time, etc. These are always encoded as uppercase
    // strings of four or more letters and so are fairly easy to spot
    try {
      dimensions = new HashMap<String, Measurement>();
      for(Map.Entry<String, Unit> unit : units.entrySet()) {
        if(unit.getKey().matches("[A-Z]{4,}")) {
          Measurement v = Measurement.parse(unit.getKey(), 0, this);
          v.completereduce();
          dimensions.put(unit.getKey().toLowerCase(), v);
        }
      }
    } catch(Parser.Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Attempt to parse the string as a unit, using the provided double as the
   * amount of the unit in the returned measurement. Parsing does not have to
   * consume the entire string in order for a measurement to be found and
   * returned. Parsing will start from the beginning of the string.
   * 
   * @param amount
   *          the amount of the unit in the measurement
   * @param text
   *          the text to parse
   * @return a measurement if one is found, null otherwise
   */
  public Measurement parse(double amount, String text) {
    return parse(amount, text, 0);
  }

  /**
   * Attempt to parse the string as a unit, using the provided double as the
   * amount of the unit in the returned measurement. Parsing does not have to
   * consume the entire string in order for a measurement to be found and
   * returned.
   * 
   * @param amount
   *          the amount of the unit in the measurement
   * @param text
   *          the text to parse
   * @param index
   *          the index within the text to start parsing from
   * @return a measurement if one is found, null otherwise
   */
  public Measurement parse(double amount, String text, int index) {
    Measurement v = Measurement.fromAmountAndString(amount, text, index, this);
    if(v == null) return null;
    if(v.getNormalizedUnit().trim().equals("")) return null;
    if(commonWords.contains(v.origText)) return null;
    return v;
  }

  protected Map<String, Unit> units = null;

  protected Map<String, Prefix> prefixes = null;

  protected Map<String, DefinedFunction> functions = null;

  protected Map<String, BuiltInFunction> builtInFunctions = null;

  protected Unit radian = null;

  protected boolean isBuiltInFunction(String word) {
    return builtInFunctions.containsKey(word);
  }

  protected BuiltInFunction getBuiltInFunction(String word) {
    return builtInFunctions.get(word);
  }

  /**
   * Checks all tables. (Originally 'checkunits'.) <br>
   * Cycles through all units and prefixes and attempts to reduce each one to 1.
   * Prints a message for all units which do not reduce to 1. NOTE: this reports
   * errors for the default units file that we should probably think about
   * trying to fix
   */
  protected void check() {
    // ---------------------------------------------------------------
    // Check all functions for valid definition and correct inverse.
    // ---------------------------------------------------------------
    for(DefinedFunction df : functions.values())
      df.check();
    // ---------------------------------------------------------------
    // Now check all units for validity
    // ---------------------------------------------------------------
    for(Unit u : units.values())
      u.check();
    // ---------------------------------------------------------------
    // Check prefixes
    // ---------------------------------------------------------------
    for(Prefix p : prefixes.values())
      p.check();
  }

  void readunits(URL name, String encoding, String locale) throws IOException {
    if(name == null) throw new IOException("no units file specified!");

    final String WHITE = " \t"; // Whitespace characters
    // ---------------------------------------------------------------
    // Open the file.
    // ---------------------------------------------------------------
    BufferedReader reader = open(name, encoding);
    if(reader == null) { throw new IOException("File '" + name + "' not found."); }
    // ---------------------------------------------------------------
    // Line numbers.
    // ---------------------------------------------------------------
    int linenum = 0; // Current line number
    int linestart = 0; // Number of line being continued
    int pos = 0;
    int startpos = 0;
    // ---------------------------------------------------------------
    // Indicator: if true we are currently reading data
    // for the wrong locale so we should skip it.
    // ---------------------------------------------------------------
    boolean wronglocale = false;
    // ---------------------------------------------------------------
    // Indicator: if true we are currently reading data
    // for some locale (right or wrong).
    // ---------------------------------------------------------------
    boolean inlocale = false;
    // ---------------------------------------------------------------
    // Buffer to accumulate the contents.
    // ---------------------------------------------------------------
    StringBuffer fb = new StringBuffer();
    // ---------------------------------------------------------------
    // Reading loop.
    // ---------------------------------------------------------------
    readLoop: while(true) {
      // -------------------------------------------------------------
      // Get one complete line (with continuations).
      // -------------------------------------------------------------
      linestart = linenum;
      startpos = pos;
      String line;
      StringBuffer sb = new StringBuffer();
      boolean haveLine = false;
      while(!haveLine) {

        line = reader.readLine();

        if(line == null) // End of file
        {
          if(sb.length() == 0) // Not looking for continuation
            break readLoop;

          throw new IOException("Missing continuation to last line of " + name
                  + ".");

        }
        fb.append(line).append("\n");
        linenum++;
        pos += (line.length() + 1);
        if(line.endsWith("\\"))
          sb.append(line.substring(0, line.length() - 1).trim()).append(" ");
        else {
          sb.append(line.trim());
          haveLine = true;
        }
      }
      // -------------------------------------------------------------
      // StringBuffer sb contains now a complete line.
      // -------------------------------------------------------------
      line = sb.toString();
      // -------------------------------------------------------------
      // Remove comment at the end of line (if any).
      // -------------------------------------------------------------
      int cmt = line.indexOf('#');
      if(cmt >= 0) line = line.substring(0, cmt).trim();
      // -------------------------------------------------------------
      // Skip empty line.
      // -------------------------------------------------------------
      if(line.length() == 0) continue;
      // -------------------------------------------------------------
      // If line is a units.dat command, process it.
      // -------------------------------------------------------------
      if(line.charAt(0) == '!') {
        int i = Util.indexOf(WHITE, line, 1);
        String command = line.substring(1, i);
        // -----------------------------------------------------------
        // Process '!locale'
        // -----------------------------------------------------------
        if(command.equals("locale")) {
          String argument = line.substring(i, line.length()).trim();
          i = Util.indexOf(WHITE, argument, 0);
          argument = argument.substring(0, i);
          if(argument.length() == 0) {
            System.err.println("No locale specified on line " + linenum
                    + " of '" + name + "'.");
            continue;
          }
          if(inlocale) {
            System.err.println("Nested locales not allowed, line " + linenum
                    + " of '" + name + "'.");
            continue;
          }
          inlocale = true;
          if(!argument.equals(locale)) wronglocale = true;
          continue;
        }
        // -----------------------------------------------------------
        // Process '!endlocale'
        // -----------------------------------------------------------
        if(command.equals("endlocale")) {
          if(!inlocale) {
            System.err.println("Unmatched !endlocale on line " + linenum
                    + " of '" + name + "'.");
            continue;
          }
          inlocale = false;
          wronglocale = false;
          continue;
        }
        // -----------------------------------------------------------
        // Process '!include', but only in right locale.
        // -----------------------------------------------------------
        if(command.equals("include")) {
          if(wronglocale) continue;
          String argument = line.substring(i, line.length()).trim();
          i = Util.indexOf(WHITE, argument, 0);
          argument = argument.substring(0, i);
          try {
            readunits(new URL(name, argument), encoding, locale);
          } catch(MalformedURLException e) {
            // I'm just hoping this never occurs
          }
          continue;
        }
        // -----------------------------------------------------------
        // Process invalid command.
        // -----------------------------------------------------------
        System.err.println("Invalid command '!" + command + "' in line "
                + linestart + " of units file '" + name + "'.");
        continue;
      }
      // -------------------------------------------------------------
      // Skip the line if wrong locale.
      // -------------------------------------------------------------
      if(wronglocale) continue;
      // -------------------------------------------------------------
      // The line is definition of unit, prefix, function, or table.
      // Split it into name and definition.
      // -------------------------------------------------------------
      int i = Util.indexOf(WHITE, line, 0);
      String unitname = line.substring(0, i).trim();
      String unitdef = line.substring(i, line.length()).trim();
      if(unitdef.length() == 0) {
        System.err.println("Missing definition in line " + linestart
                + " of units file '" + name + "'.");
        continue;
      }
      // -------------------------------------------------------------
      // Enter definition from the line into tables.
      // -------------------------------------------------------------
      Location loc =
              new Location(name.toString(), linestart, startpos, pos - 1);
      // If line is a prefix definition:
      if(Prefix.accept(unitname, unitdef, loc, this)) continue;
      // If line is a table definition:
      if(TabularFunction.accept(unitname, unitdef, loc, this)) continue;
      // If line is a function definition:
      if(ComputedFunction.accept(unitname, unitdef, loc, this)) continue;
      // Otherwise line is a unit definition.
      Unit.accept(unitname, unitdef, loc, this);
    } // end ReadLoop
    // ---------------------------------------------------------------
    // Close the file.
    // ---------------------------------------------------------------
    try {
      reader.close();
    } catch(IOException e) {
      e.printStackTrace();
    }
  }

  private static BufferedReader open(final URL name, String encoding) {
    BufferedReader reader = null;

    try {
      InputStream s = name.openStream();
      if(s != null) {
        InputStreamReader isr = new InputStreamReader(s, encoding);
        reader = new BufferedReader(isr);
        return reader;
      }
    } catch(UnsupportedEncodingException e) {
      // should never happen
      // e.printStackTrace();
    } catch(IOException e) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    }
    return null;
  }

  protected String getNormalisedUnitName(String name) {

    Unit u = Unit.find(name, this);
    if(u == null) return null;
    return u.name;
  }
  
  public static void main(String args[]) throws MalformedURLException, IOException {
    MeasurementsParser parser = new MeasurementsParser((new File("plugins/Tagger_Measurements/resources/units.dat")).toURI().toURL(), (new File("plugins/Tagger_Measurements/resources/common_words.txt")).toURI().toURL());
    Measurement m = parser.parse(20, "mg p");   
    System.out.println(m.getParsedText());
  }
}
