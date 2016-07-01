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

import java.util.List;
import java.util.Vector;

/**
 * A function defined by a table (a 'piecewise linear unit'.)
 */
class TabularFunction extends DefinedFunction {

  private double[] xValues;

  private double[] yValues;

  private String tableunit;

  /**
   * Return signum of double float number 'n'.
   */
  private static int signum(double n) {
    return n == 0 ? 0 : (n > 0 ? 1 : -1);
  }

  /**
   * Construct object for function 'nam' defined at 'loc'. Dimension of the
   * result is defined by unit expression 'u', and the table is given by the
   * Vectors 'x' and 'y'.
   */
  TabularFunction(String nam, String u, Vector<Double> x, Vector<Double> y,
          MeasurementsParser gnuUnits) {
    super(nam, gnuUnits);
    tableunit = u;
    int n = x.size();
    xValues = new double[n];
    yValues = new double[n];
    for(int i = 0; i < n; i++) {
      xValues[i] = x.elementAt(i).doubleValue();
      yValues[i] = y.elementAt(i).doubleValue();
    }
  }

  /**
   * Given is a line from units.dat file, parsed into name 'nam' and definition
   * 'df'. If this line defines a tabular function, construct a TabularFunction
   * object defined by it, it into user function table, and return true.
   * Otherwise return false.
   */
  static boolean accept(final String nam, final String df, Location loc,
          MeasurementsParser gnuUnits) {
    // If unit name contains '[', we have a table definition.
    int leftParen = nam.indexOf('[');
    int rightParen = nam.indexOf(']', leftParen + 1);
    if(leftParen < 0) return false;
    // Get function name and unit
    if(rightParen != nam.length() - 1 || rightParen == leftParen + 1) {
      System.err.println("Bad function definition of '" + nam + "' on line "
              + loc.lineNum + " ignored.");
      return true;
    }
    String funcname = nam.substring(0, leftParen);
    String tabunit = nam.substring(leftParen + 1, rightParen);
    // Is it redefinition?
    if(gnuUnits.functions.containsKey(funcname)) {
      System.err.println("Redefinition of function '" + funcname + "' on line "
              + loc.lineNum + " is ignored.");
      return true;
    }
    Vector<Double> x = new Vector<Double>();
    Vector<Double> y = new Vector<Double>();
    int p = 0;
    while(p < df.length()) {
      int q = Util.strtod(df, p);
      if(p == q) break;
      x.addElement(new Double(df.substring(p, q).trim()));
      p = q;
      q = Util.strtod(df, p);
      if(p == q) {
        System.err.println("Missing last value after "
                + (x.lastElement()).doubleValue() + ".\n"
                + "Definition of function '" + nam + "' on line " + loc.lineNum
                + " is ignored.");
        return true;
      }
      y.addElement(new Double(df.substring(p, q).trim()));
      p = q;
      if(p >= df.length()) break;
      if(df.charAt(p) == ',') p++;
    }
    // Install function in table.
    gnuUnits.functions.put(funcname, new TabularFunction(funcname, tabunit, x,
            y, gnuUnits));
    return true;
  }

  /**
   * Apply the function to Value 'v' (with result in 'v').
   */
  @Override
  void applyTo(Measurement v) throws Parser.Exception {
    Measurement dim = null;
    try {
      dim = Measurement.parse(tableunit, 0, gnuUnits);
    } catch(Parser.Exception e) {
      throw new Parser.Exception("Invalid dimension, " + dim + ", of function "
              + name + ". " + e.getMessage());
    }
    if(!v.isNumber())
      throw new Parser.Exception("Argument " + v + " of " + name
              + " is not a number.");
    double result = interpolate(v, v.factor, xValues, yValues, "");
    dim.factor *= result;
    v.copyFrom(dim);
  }

  /**
   * Apply inverse of the function to Value 'v' (with result in 'v').
   */
  @Override
  void applyInverseTo(Measurement v) throws Parser.Exception {
    Measurement dim = null;
    try {
      dim = Measurement.parse(tableunit, 0, gnuUnits);
    } catch(Parser.Exception e) {
      throw new Parser.Exception("Invalid dimension, " + dim
              + ", of function ~" + name + ". " + e.getMessage());
    }
    Measurement n = new Measurement(v);
    n.div(dim);
    if(!n.isNumber())
      throw new Parser.Exception("Argument " + v + " of function ~" + name
              + " is not conformable to " + dim + ".");
    double result = interpolate(v, n.factor, yValues, xValues, "~");
    v.copyFrom(new Measurement(gnuUnits));
    v.factor = result;
  }

  /**
   * Check the definition. Used in 'checkunits'.
   */
  @Override
  void check() {
    // Check for monotonicity which is needed for unique inverses
    if(xValues.length <= 1) {
      System.err.println("Table '" + name + "' has only one data point");
      return;
    }
    int direction = signum(yValues[1] - yValues[0]);
    for(int i = 2; i < xValues.length; i++)
      if(direction == 0 || signum(yValues[i] - yValues[i - 1]) != direction) {
        System.err.println("Table '" + name
                + "' lacks unique inverse around entry "
                + Util.shownumber(xValues[i - 1]));
        return;
      }
    return;
  }

  /**
   * If function defined by this object is compatible with Value 'v', add this
   * object to 'list'. Used in 'tryallunits'.
   */
  @Override
  void addtolist(final Measurement v, List<Entity> list) {
    Measurement thisvalue = Measurement.fromString(tableunit, gnuUnits);
    if(thisvalue == null) return;
    if(thisvalue.isCompatibleWith(v, Ignore.DIMLESS)) insertAlph(list);
  }

  /**
   * Return short description of this object to be shown by 'tryallunits'.
   */
  @Override
  String desc() {
    return "<piecewise linear unit>";
  }

  /**
   * The arrays 'x' and 'y' contain x-values and corresponding y-values. Find,
   * by linear interpolation, the y-value corresponding to the x-value given by
   * the factor of Value object 'v'. The argument 'inv' is either empty string
   * or '~', and is used in the error message to show if we are doing inverse or
   * not.
   */
  private double interpolate(Measurement v, double xval, double[] x,
          double[] y, String inv) throws Parser.Exception {
    for(int i = 0; i < x.length - 1; i++)
      if((x[i] <= xval && xval <= x[i + 1])
              || (x[i] >= xval && xval >= x[i + 1]))
        return y[i] + (xval - x[i]) * (y[i + 1] - y[i]) / (x[i + 1] - x[i]);
    throw new Parser.Exception("Argument " + v + " is outside the domain of "
            + inv + name + ".");
  }
}
