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

/**
 * A function defined by an expression (a 'nonlinear unit'.)
 */
class ComputedFunction extends DefinedFunction {
  private FuncDef forward; // Forward definition

  private FuncDef inverse; // Inverse definition

  ComputedFunction(String nam, String fpar, String fdf, String fdim,
          String ipar, String idf, String idim, MeasurementsParser gnuUnits) {
    super(nam, gnuUnits);
    forward = new FuncDef(fpar, fdf, fdim);
    inverse = new FuncDef(ipar, idf, idim);
  }

  /**
   * Given is a line from units.dat file, parsed into name 'nam' and definition
   * 'df'. If this line defines a computed function, construct a
   * ComputedFunction object defined by it, enter it into user function table,
   * and return true. Otherwise return false.
   */
  static boolean accept(final String nam, final String df, Location loc,
          MeasurementsParser gnuUnits) {
    // If unitname contains '(', we have a function definition.
    // Syntax is: funcname(param) [fwddim;invdim] fwddef ; invdef
    // [fwddim;invdim] may be omitted.
    // ; invdef may be omitted.
    int leftParen = nam.indexOf('(');
    int rightParen = nam.indexOf(')', leftParen + 1);
    if(leftParen < 0) return false;
    // Get function name and parameter
    if(rightParen != nam.length() - 1 || rightParen == leftParen + 1) {
      System.err.println("Bad function definition of '" + nam + "' on line "
              + loc.lineNum + " ignored.");
      return true;
    }
    String funcname = nam.substring(0, leftParen);
    String param = nam.substring(leftParen + 1, rightParen);
    // Get dimensions
    String unitdef = df;
    String fwddim = null;
    String invdim = null;
    if(df.charAt(0) == '[') {
      int semicol = df.indexOf(';', 1);
      int rightBracket = df.indexOf(']', semicol + 1);
      if(semicol < 0 || rightBracket < 0) {
        System.err.println("Bad dimension of function '" + nam + "' on line "
                + loc.lineNum + ".Function ignored.");
        return true;
      }
      if(semicol > 1) fwddim = df.substring(1, semicol);
      if(rightBracket - semicol > 1)
        invdim = df.substring(semicol + 1, rightBracket);
      unitdef = df.substring(rightBracket + 1, df.length()).trim();
    }
    // Get definitions
    String fwddef = null;
    String invdef = null;
    int semicol = unitdef.indexOf(';');
    if(semicol < 0)
      fwddef = unitdef;
    else {
      fwddef = unitdef.substring(0, semicol).trim();
      invdef = unitdef.substring(semicol + 1, unitdef.length()).trim();
    }
    // Is it redefinition?
    if(gnuUnits.functions.containsKey(funcname)) {
      System.err.println("Redefinition of function '" + funcname + "' on line "
              + loc.lineNum + " is ignored.");
      return true;
    }
    // Install function in table.
    gnuUnits.functions.put(funcname, new ComputedFunction(funcname, param,
            fwddef, fwddim, funcname, invdef, invdim, gnuUnits));
    return true;
  }

  /**
   * Apply the function to Value 'v' (with result in 'v').
   */
  @Override
  void applyTo(Measurement v) throws Parser.Exception {
    forward.applyTo(v, "");
  }

  /**
   * Apply inverse of the function to Value 'v' (with result in 'v').
   */
  @Override
  void applyInverseTo(Measurement v) throws Parser.Exception {
    inverse.applyTo(v, "~");
  }

  /**
   * Check the function. Used in 'checkunits'.
   */
  @Override
  void check() {
    Measurement v;
    if(forward.dimen != null) {
      try {
        v = Measurement.parse(forward.dimen, 0, gnuUnits);
        v.completereduce();
      } catch(Parser.Exception e) {
        System.err.println("Function '" + name + "' has invalid type '"
                + forward.dimen + "'");
        return;
      }
    } else v = new Measurement(gnuUnits);
    v.factor *= 7; // Arbitrary choice where we evaluate inverse
    Measurement saved = new Measurement(v);
    try {
      applyTo(v);
    } catch(Parser.Exception e) {
      System.err.println("Error in definition " + name + "(" + forward.param
              + ") as " + forward.def);
      return;
    }
    if(inverse.def == null) {
      System.err.println("Warning: no inverse for function '" + name + "'");
      return;
    }
    try {
      applyInverseTo(v);
      v.div(saved);
      v.completereduce();
      double delta = v.factor - 1;
      if(!v.isNumber() || delta < -1e-12 || delta > 1e-12)
        System.err.println("Inverse is not the inverse for function '" + name
                + "'");
    } catch(Parser.Exception e) {
      System.err.println("Error in inverse ~" + name + "(" + inverse.param
              + ") as " + inverse.def);
    }
  }

  /**
   * If function defined by this object is compatible with Value 'v', add this
   * object to 'list'. Used in 'tryallunits'.
   */
  @Override
  void addtolist(final Measurement v, List<Entity> list) {
    if(inverse.dimen == null) return;
    Measurement thisvalue = Measurement.fromString(inverse.dimen, gnuUnits);
    if(thisvalue == null) return;
    if(thisvalue.isCompatibleWith(v, Ignore.DIMLESS)) insertAlph(list);
  }

  /**
   * Return short description of this object to be shown by 'tryallunits'.
   */
  @Override
  String desc() {
    return "<nonlinear unit>";
  }

  /**
   * Holds details of a forward or inverse definition of the function.
   */
  private class FuncDef {
    String param;

    String def;

    String dimen;

    /**
     * Construct FuncDef object for definition srting 'df' with parameter 'par'
     * of dimension 'dim'.
     */
    FuncDef(String par, String df, String dim) {
      param = par;
      def = df;
      dimen = dim;
    }

    /**
     * Apply the function (or inverse) defined by the object to Value 'v'.
     */
    void applyTo(Measurement v, String inv) throws Parser.Exception {
      v.completereduce();
      if(dimen != null) {
        Measurement dim;
        try {
          dim = Measurement.parse(dimen, 0, gnuUnits);
        } catch(Parser.Exception e) {
          throw new Parser.Exception("Invalid dimension, " + dimen
                  + ", of function " + inv + name + ". " + e.getMessage());
        }
        dim.completereduce();
        if(!dim.isCompatibleWith(v, Ignore.NONE))
          throw new Parser.Exception("Argument " + v + " of function " + inv
                  + name + " is not conformable to " + dim + ".");
      }
      Measurement result;
      try {
        result = Measurement.parse(def, param, v, gnuUnits);
      } catch(Parser.Exception e) {
        throw new Parser.Exception("Invalid definition of function '" + inv
                + name + "'. " + e.getMessage());
      }
      v.copyFrom(result);
    }
  }
}
