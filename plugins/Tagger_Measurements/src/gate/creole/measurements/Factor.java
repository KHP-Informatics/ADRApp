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

/**
 * An entity that can be a factor in a Product: a unit or a prefix.
 */
abstract class Factor extends Entity {
  /** Definition string. */
  String def;

  /** Is this a primitive unit? */
  boolean isPrimitive = false;

  /** Is this a dimensionless primitive unit? */
  boolean isDimless = false;

  /** Is the definition a number? */
  boolean isNumber = false;

  /** Ignore in comparisons? */
  boolean ignoredIf(Ignore what) {
    if(what == Ignore.PRIMITIVE && isPrimitive) return true;
    if(what == Ignore.DIMLESS && isDimless) return true;
    return false;
  }

  /**
   * Construct object for factor 'nam' appearing at 'loc'. The factor is defined
   * by string 'df'.
   */
  Factor(final String nam, final String df, MeasurementsParser gnuUnits) {
    super(nam, gnuUnits);
    def = df;
    if(df.equals("!")) isPrimitive = true;
    if(df.equals("!dimensionless")) {
      isPrimitive = true;
      isDimless = true;
    }
    isNumber = Util.strtod(def, 0) == def.length();
  }

  /**
   * Find out if 'name' is the name of a unit or prefix, or is a prefixed unit
   * name. The unit name given as 'name' or its part may be in plural. Return a
   * two-element array where first element is the Prefix (or null if none) and
   * second is the Unit (or null if none). Return null if 'name' is not
   * recognized. (Originally part of 'lookupunit'.)
   */
  static Factor[] split(final String name, MeasurementsParser gnuUnits) {
    // ---------------------------------------------------------------
    // If 'name' is a unit name, possibly in plural form,
    // return its Unit object.
    // ---------------------------------------------------------------
    Unit u = Unit.find(name, gnuUnits);
    if(u != null) return new Factor[]{null, u};
    // ---------------------------------------------------------------
    // The 'name' is not a unit name.
    // See if it is a prefix or prefixed unit name.
    // ---------------------------------------------------------------
    Prefix p = Prefix.find(name, gnuUnits);
    // ---------------------------------------------------------------
    // Return null if not a prefix or prefixed unit name.
    // ---------------------------------------------------------------
    if(p == null) return null;
    // ---------------------------------------------------------------
    // Get the prefix string.
    // If it is all of 'name', return its Prefix object.
    // ---------------------------------------------------------------
    String prefix = p.name;
    if(name.equals(prefix)) return new Factor[]{p, null};
    // ---------------------------------------------------------------
    // The 'name' has a known prefix 'prefix'.
    // Split 'name' into the prefix and 'rest'.
    // If 'rest' (or its singular form) is a unit name,
    // return the Prefix and Unit objects.
    // ---------------------------------------------------------------
    String rest = name.substring(prefix.length(), name.length());
    u = Unit.find(rest, gnuUnits);
    if(u != null) return new Factor[]{p, u};
    // ---------------------------------------------------------------
    // Return null if 'rest' is not a unit name.
    // ---------------------------------------------------------------
    return null;
  }

  /**
   * If 'name' is the name of a unit or prefix, or is a prefixed unit name,
   * print its definition followed by equal sign. Repeat this for the definition
   * thus obtained. (Originally part of 'showdefinition'.)
   */
  static void showdef(final String name, MeasurementsParser gnuUnits) {
    String def = name;
    while(true) {
      Factor[] pu = split(def, gnuUnits);
      if(pu == null) break; // Not a prefix-unit
      Factor pref = pu[0];
      Factor unit = pu[1];
      if(unit == null) // Prefix only
      {
        if(pref.isNumber) break;
        def = pref.def;
      } else if(pref == null) // Unit only
      {
        if(unit.isPrimitive || unit.isNumber) break;
        def = unit.def;
      } else // Prefix and unit
      {
        def = pref.def + " " + (unit.isPrimitive ? unit.name : unit.def);
      }
      System.out.print(def + " = ");
    }
  }
}
