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

import java.util.Map;

/**
 * Instances of this class represent a single measurement as recognised by the
 * MeasurementsParser.
 * 
 * @see MeasurementsParser
 * @author Mark A. Greenwood
 */
public class Measurement {

  protected double factor;

  protected Product numerator;

  protected Product denominator;

  private double origValue;

  private String origUnit;

  protected String origText;

  protected MeasurementsParser parser;

  /**
   * Returns the value of the measurement once the unit has been normalized to it's base SI form.
   * @see #getNormalizedUnit
   * @return the value of the measurement once the unit has been normalized to it's base SI form
   */
  public Double getNormalizedValue() {
    return factor;
  }

  /**
   * Returns the unit reduced down to it's most basic form. This normalized form
   * usually consists solely of SI units. This normalized form can be used to
   * compare measurements of the same dimension specified using different units
   * as they will all reduce to the same base unit.
   * 
   * @see <a
   *      href="http://en.wikipedia.org/wiki/International_System_of_Units">SI
   *      Units</a>
   * @return the unit reduced to it's most basic form.
   */
  public String getNormalizedUnit() {
    StringBuffer sb = new StringBuffer();
    sb.append(numerator.asString());
    if(denominator.size() > 0) sb.append(" /").append(denominator.asString());
    return sb.toString().trim();
  }

  /**
   * Returns The double value of this measurement as determined from the parsed
   * text
   * 
   * @return the double value of this measurement as determined from the parsed
   *         text
   */
  public Double getValue() {
    return origValue;
  }

  /**
   * Returns the unit recognised in the parsed text. Note that this doesn't have
   * to be the same as the text that was parsed. For example, this method will
   * return "mile" even if it has parsed the string "miles"
   * 
   * @return the unit recognised in the parsed text
   */
  public String getUnit() {
    // TODO it turns out that the origUnit isn't valid as it just sorts the
    // pieces in alphabetical order which isn't really helpful, so far now we
    // just revert to using the parsed text. It would be nice if we could figure
    // out a way of normalizing units without reduction (i.e. miles --> mile)
    return origText.trim();

    //return origUnit;
  }

  /**
   * Returns the text that was parsed into a measurement
   * 
   * @return the text that was parsed into a measurement
   */
  public String getParsedText() {
    return origText;
  }

  /**
   * Constructs a Value representing dimensionless number 1. <br>
   * (Originally 'initializeunit'.)
   */
  Measurement(MeasurementsParser parser) {
    factor = 1.0;
    numerator = new Product();
    denominator = new Product();
    this.parser = parser;
  }

  /**
   * Constructs copy of a given Value.
   * 
   * @param v
   *          a Value to be copied.
   */
  Measurement(final Measurement v) {
    factor = v.factor;
    numerator = new Product(v.numerator);
    denominator = new Product(v.denominator);
    parser = v.parser;
  }

  /**
   * Makes this Value a copy of given Value.
   * 
   * @param v
   *          the Value to be copied.
   * @return this Value - now a copy of 'v'.
   */
  Measurement copyFrom(final Measurement v) {
    factor = v.factor;
    numerator = new Product(v.numerator);
    denominator = new Product(v.denominator);
    return this;
  }

  /**
   * Constructs a Value from unit expression; throws exception on error. <br>
   * The Parser.Exception is thrown if the Value cannot be constructed because
   * of incorrect syntax, unknown unit name, etc.. The exception contains a
   * complete error message. <br>
   * (Originally 'parseunit'.)
   * 
   * @param s
   *          a unit expression.
   * @return Value represented by the expression.
   */
  static Measurement parse(final String s, int index, MeasurementsParser parser)
          throws Parser.Exception {
    Lexer lex = new Lexer(); // Create Lexer..
    lex.initialize(s, index, parser); // ..and initialize it on 's'

    Parser psr = new Parser(lex, parser);

    // this may thrown an exception because of the space change.
    // i.e. it throws an exception when it parses 100 m m as the last m is a
    // unit but wasn't expected whilst it parses 10 m blah OK and returns 100 m
    // as blah isn't a unit lex.pos contains enough info to get the string
    // parsed so far but not sure what to do with that yet. Probably needs a
    // clever if somewhere in the parser or lexer to handle this
    Parser.Symbol result = psr.unitexpr();

    if(result == null)
      throw new Parser.Exception("string '" + s + "' is not a unit");

    result.value.origText = lex.getParsedText();

    return result.value;
  }

  /**
   * Constructs a Value from unit expression, substituting given Value for a
   * parameter; throws exception on error. <br>
   * The unit expression 's' may contain the string given as 'parm' at a place
   * that syntactically corresponds to a unit name. This string is then treated
   * as name of Value given as 'parmValue', rather than a unit name. <br>
   * The Parser.Exception is thrown if the Value cannot be constructed because
   * of incorrect syntax, unknown unit name, etc.. The exception contains a
   * complete error message.
   * 
   * @param s
   *          a unit expression.
   * @param parm
   *          parameter name.
   * @param parmValue
   *          Value to be substituted for 'parm'.
   * @return Value represented by the expression.
   */
  static Measurement parse(final String s, final String parm,
          final Measurement parmValue, MeasurementsParser parser)
          throws Parser.Exception {
    Lexer lex = new Lexer(); // Create Lexer
    lex.funcParm = parm; // Tell Lexer to replace..
    lex.funcParmValue = parmValue; // ..'parm' by 'parmValue'
    lex.initialize(s, 0, parser); // Initialize Lexer on 's'
    Parser psr = new Parser(lex, parser); // Create Parser
    Parser.Symbol result = psr.unitexpr(); // Parse 's' as unitexpr
    return result.value;
  }

  /**
   * Constructs a completely reduced Value from unit expression; writes mesage
   * on error. <br>
   * If the Value cannot be constructed because of incorrect syntax, unknown
   * unit name, etc., writes an error message and returns null. <br>
   * (Originally 'processunit'.)
   * 
   * @param s
   *          a unit expression.
   * @return Value represented by the expression, or null if the Value could not
   *         be constructed.
   */
  static Measurement fromString(final String s, int index,
          MeasurementsParser parser) {
    try {
      Measurement v = parse(s, index, parser);
      v.completereduce();
      DefinedFunction func = parser.functions.get(v.getNormalizedUnit());
      if(func != null) {
        Measurement fv = new Measurement(parser);
        fv.origText = v.origText;
        fv.factor = v.factor;
        func.applyTo(fv);
        return fv;
      }
      return v;
    } catch(Parser.Exception e) {
      // e.printStackTrace();
      return null;
    }
  }

  static Measurement fromString(final String s, MeasurementsParser parser) {
    return fromString(s, 0, parser);
  }

  /**
   * Constructs a completely reduced Value from a double amount ans a unit
   * expression; writes mesage on error. <br>
   * If the Value cannot be constructed because of incorrect syntax, unknown
   * unit name, etc., writes an error message and returns null. <br>
   * 
   * @param amount
   *          a double representing the amount
   * @param unit
   *          a unit expression.
   * @return Value represented by the amount and unit, or null if the Value
   *         could not be constructed.
   */
  static Measurement fromAmountAndString(double amount, String unit, int index,
          MeasurementsParser parser) {
    try {
      Measurement v = parse(unit, index, parser);
      v.factor = amount;
      v.completereduce();
      DefinedFunction func = parser.functions.get(v.getNormalizedUnit());
      if(func != null) {
        Measurement fv = new Measurement(parser);
        fv.factor = amount;
        fv.origText = v.origText;
        func.applyTo(fv);
        return fv;
      }
      return v;
    } catch(Parser.Exception e) {
      // e.printStackTrace();
      return null;
    }
  }

  /**
   * Constructs a Value from a string that may be name of a unit or a prefix, or
   * a prefixed unit name, possibly in plural from. Throws exception if the
   * string is none of them.
   * 
   * @param s
   *          possible name of a unit, prefix, or prefixed unit.
   * @return Value represented by 's'.
   */
  static Measurement fromName(final String s, MeasurementsParser parser)
          throws Parser.Exception {
    Factor[] pu = Factor.split(s, parser);
    if(pu == null)
      throw new Parser.Exception("2: Unit '" + s + "' is unknown.");
    Measurement v = new Measurement(parser);
    if(pu[0] != null) v.numerator.add(pu[0]);
    if(pu[1] != null) v.numerator.add(pu[1]);
    return v;
  }

  /**
   * Constructs a printable string representation of this measurement
   * 
   * @return printable representation of this measurement
   */
  @Override
  public String toString() {
    return Util.shownumber(factor) + " " + getNormalizedUnit();
  }

  /**
   * Checks if this Value is compatible with another Value. <br>
   * Two Values are compatible if they have compatible numerators and
   * denominators. <br>
   * (Originally 'compareunits'.)
   * 
   * @param v
   *          Value to be checked against.
   * @return <code>true</code> if the Values are compatible, or
   *         <code>false</code> otherwise.
   */
  boolean isCompatibleWith(final Measurement v, Ignore ignore) {
    return numerator.isCompatibleWith(v.numerator, ignore)
            && denominator.isCompatibleWith(v.denominator, ignore);
  }

  /**
   * Reduces this Value and checks if it represents a number. <br>
   * A Value represents a number if it is dimensionless, that is, its numerator
   * and denominator are both empty.
   * 
   * @return <code>true</code> if the Value represents a number, or
   *         <code>false</code> otherwise.
   */
  boolean isNumber() throws Parser.Exception {
    completereduce();
    return numerator.size() == 0 && denominator.size() == 0;
  }

  /**
   * Adds given Value to this Value. <br>
   * (Originally 'addunit'.)
   * 
   * @param v
   *          Value to be added.
   */
  void add(final Measurement v) throws Parser.Exception {
    completereduce();
    v.completereduce();
    if(!isCompatibleWith(v, Ignore.NONE))
      throw new Parser.Exception("Sum of non-conformable values:\n\t" + this
              + "\n\t" + v + ".");
    factor += v.factor;
  }

  /**
   * Multiplies this Value by a given Value. <br>
   * (Originally 'multunit'.)
   * 
   * @param v
   *          Value to multiply by.
   */
  void mult(final Measurement v) {
    factor *= v.factor;
    numerator.add(v.numerator);
    denominator.add(v.denominator);
  }

  /**
   * Divide this Value by a given Value. <br>
   * (Originally 'divunit'.)
   * 
   * @param v
   *          Value to divide by.
   */
  void div(final Measurement v) throws Parser.Exception {
    if(v.factor == 0)
      throw new Parser.Exception("Division of " + this + " by zero (" + v
              + ").");
    factor /= v.factor;
    denominator.add(v.numerator);
    numerator.add(v.denominator);
  }

  /**
   * Inverts this Value. <br>
   * (Originally 'invertunit'.)
   */
  void invert() throws Parser.Exception {
    if(factor == 0)
      throw new Parser.Exception("Division by zero (" + this + ").");
    factor = 1.0 / factor;
    Product num = numerator;
    numerator = denominator;
    denominator = num;
  }

  /**
   * Raises this Value to integer power n>=0. <br>
   * (Originally 'expunit').
   * 
   * @param n
   *          the exponent.
   */
  void power(int n) throws Parser.Exception {
    if(n < 0) throw new Parser.Exception("Negative exponent, " + n + ".");
    Product num = new Product();
    Product den = new Product();
    double fac = 1.0;
    for(int i = 0; i < n; i++) {
      fac *= factor;
      num.add(numerator);
      den.add(denominator);
    }
    factor = fac;
    numerator = num;
    denominator = den;
  }

  /**
   * Raises this Value to power specified by another Value. The Value supplied
   * as exponent must represent a number. If that number is not an integer or a
   * fraction 1/integer, this Value must represent a number. <br>
   * (Originally 'unitpower'.)
   * 
   * @param v
   *          the exponent.
   */
  void power(final Measurement v) throws Parser.Exception {
    if(!v.isNumber())
      throw new Parser.Exception("Non-numeric exponent, " + v + ", of " + this
              + ".");
    double p = v.factor;
    if(Math.floor(p) == p) // integer exponent
      power((int)Math.abs(p));
    else if(Math.floor(1.0 / p) == 1.0 / p) // fractional exponent
      root((int)Math.abs(1.0 / p));
    else {
      if(!isNumber())
        throw new Parser.Exception("Non-numeric base, " + this
                + ", for exponent " + v + ".");
      factor = Math.pow(factor, Math.abs(p));
    }
    if(p < 0) invert();
  }

  /**
   * Computes the n-th root of this Value for an integer n. The integer n must
   * not be 0. If n is even, this Value must be non-negative. <br>
   * (Originally 'rootunit'.)
   * 
   * @param n
   *          the exponent.
   */
  void root(int n) throws Parser.Exception {
    if(n == 0 || (n % 2 == 0 && factor < 0))
      throw new Parser.Exception("Illegal n-th root of " + this + ", n= " + n
              + ".");
    completereduce();
    Product num = numerator.root(n);
    Product den = denominator.root(n);
    if(num == null || den == null) {
      String nth = n == 2 ? "square" : (n == 3 ? "cube" : n + "-th");
      throw new Parser.Exception(this + " is not a " + nth + " root.");
    }
    factor = Math.pow(factor, 1.0 / n);
    numerator = num;
    denominator = den;
  }

  /**
   * Removes factors that appear in both the numerator and denominator. <br>
   * (Originally 'cancelunit'.)
   */
  void cancel() {
    int den = 0;
    int num = 0;
    while(num < numerator.size() && den < denominator.size()) {
      int comp =
              (denominator.factor(den).name)
                      .compareTo(numerator.factor(num).name);
      if(comp == 0) { // units match, so cancel them.
        denominator.delete(den);
        numerator.delete(num);
      } else if(comp < 0) // Move up whichever index is alphabetically..
        den++; // ..behind to look for future matches.
      else num++;
    }
  }

  /**
   * Reduces numerator or denominator of this Value to primitive units.
   * 
   * @param flip
   *          indicates whether to reduce the numerator (
   *          <code>flip = false</code>) or denominator (
   *          <code>flip = true</code>).
   * @return <code>true</code> if reduction was performed, or <code>false</code>
   *         if there is nothing more to reduce.
   */
  boolean reduceproduct(boolean flip) throws Parser.Exception {
    boolean didsomething = false;
    Product prod = (flip ? denominator : numerator);
    if(flip)
      denominator = new Product();
    else numerator = new Product();
    Product newprod = (flip ? denominator : numerator);
    // ---------------------------------------------------------------
    // Process all factors of 'prod'
    // ---------------------------------------------------------------
    for(int f = 0; f < prod.size(); f++) {
      Factor fact = prod.factor(f);
      String toadd = fact.name;
      if(toadd == null) // Is this possible?
        throw new Parser.Exception("1: Unknown Unit.");
      if(fact.isPrimitive) {
        newprod.add(fact);
        continue;
      }
      Measurement newval;
      try {
        newval = parse(fact.def, 0, parser);
      } catch(Parser.Exception e) {
        throw new Parser.Exception("Invalid definition of '" + toadd + "'. "
                + e.getMessage());
      }
      if(flip)
        div(newval);
      else mult(newval);
      didsomething = true;
    } // end process all factors
    return didsomething;
  }

  /**
   * Reduces this Value as much as possible.
   */
  void completereduce() throws Parser.Exception {
    origValue = factor;

    // TODO can we do this in such a way that hours becomes hour etc.?
    origUnit = getNormalizedUnit();

    /* Keep calling reduceproduct until it doesn't do anything */
    while(true) {
      boolean topchanged = reduceproduct(false);
      boolean botchanged = reduceproduct(true);
      if(!topchanged && !botchanged) break;
    }
    cancel();
  }

  /**
   * Returns the dimension (or type) of the measurement, such as length, time, area, etc.
   * @return the dimension (or type) of the measurement, such as length, time, area, etc.
   */
  public String getDimension() {

    for(Map.Entry<String, Measurement> d : parser.dimensions.entrySet()) {
      if(isCompatibleWith(d.getValue(), Ignore.NONE)) return d.getKey();
    }
    return null;
  }
}
